# 나의 영토 운영 메모

설계 문서(PDF 2종)를 보완하는 운영 주의 사항. 단계별 QA 에서 "수용 — 기록만"으로 결정된 위험과 운영 절차를 모은다.

## 수용한 위험(4단계 QA r2)

| # | 내용 | 대응 |
|---|------|------|
| N3 | 랜덤 handle(`explorer-xxxx`, 4자 × 32문자 ≈ 100만)은 훑어서 찾을 수 있다. 기본 공개 범위가 PRIVATE 라 "공개하기"를 직접 켠 사람만 해당한다. | 수용. 프로필을 공개하는 사용자에게 handle 을 바꾸도록 안내(프로필 탭 handle 변경). `/u/**` 요청 수 제한은 게이트웨이(리버스 프록시)에서. |
| N4 | `spring.profiles.default: local` — 프로파일 지정을 빠뜨리고 기동하면 local yml(`territory.dev.enabled: true`)이 켜져 `/dev/**`(dev 로그인 포함)가 열린다. | 수용. **운영 기동은 반드시 `--spring.profiles.active=prod`**(또는 `SPRING_PROFILES_ACTIVE=prod`). 배포 스크립트·컨테이너 이미지에 고정하고, 기동 후 `/dev/login` 이 404 인지 확인한다. |
| — | 단일 인스턴스 가정: 세션 메모리, 재계산 예약·outbox 릴레이(SKIP LOCKED 없음), VS 카드 캐시·소셜 통계 캐시가 인스턴스별. | 다중 인스턴스 전에 세션 저장소·릴레이 잠금·공유 캐시(Redis) 도입. |

## 5단계 소셜

- **친구 소식(feed_entry)은 읽기 모델**이다. outbox 행은 지우지 않으므로 언제든 비우고 다시 재생해 만들 수 있다(app-api `OutboxReplay`, local `POST /dev/rebuild/feed`). 새 구독자 `social.feed` 는 배포 이전에 이미 발행된 이벤트를 받지 않으므로, **5단계 배포 직후 한 번 재구성**한다:
  - `POST /admin/rebuild/feed`(헤더 `X-Admin-Token`, 세션 없이) → 202, `GET /admin/rebuild/feed` 로 상태(RUNNING → SUCCEEDED/FAILED, 재생·건너뜀 수) 확인. 동시에 하나만(진행 중이면 409).
  - 재구성 동안 릴레이는 `social.feed` 몫만 대기시키고(즉시 효력 — 진행 중인 주기를 기다리지 않음) 다른 구독자(진행·꾸미기)는 계속 처리한다. 그 몫만 남은 행은 주기마다 id 만 읽고 건너뛴다(대기 3,000행 기준 릴레이 한 주기 101ms → 14ms). 피드는 이전 세대가 그대로 보이고, 다 쌓이면 새 세대로 바뀐 뒤 대기분이 이어 전달된다. 실패하면 쌓던 세대만 버린다(이전 피드 보존). 처리 실패(`skipped`)가 `territory.social.feed-rebuild.max-skipped`(기본 0)를 넘으면 FAILED 로 이전 세대를 유지한다 — 로그(`outbox 재생 건너뜀`)로 원인을 고친 뒤 다시 돌린다. 읽을 수 없는 예전 행(`unreadable`)은 세기만 하고 교체를 막지 않는다.
  - 단일 인스턴스 가정 — 여러 인스턴스면 다른 인스턴스의 릴레이는 멈추지 못한다(그 인스턴스가 바뀌기 전 옛 세대에 쓴 소식은 버려진다 → 그 경우 재구성을 한 번 더).
- **상위 %·지역별 방문자 비율·시·도 평균**(모집단 = 활성 지역 1곳 이상인 활성 탐험가)은 일 1회 배치(지역 방문자 수가 모집단을 넘는 이상치는 그 지역만 맞추고 ERROR 로그 `모집단으로 맞췄다` + `RankBatchJob.overflowedRegions()` 카운터 — 원천 확인)(`territory.social.rank-batch-cron`, 기본 매일 04:30 Asia/Seoul, `-` 면 끔)가 explorer_region 에서 통째로 다시 만든다. 설계의 Redis 대신 DB(rank_percentile·region_stats·province_stats) + 애플리케이션 캐시(`territory.social.stats-cache-ttl`, 기본 5분) — **Redis 는 트래픽이 생긴 뒤** 도입한다. 배치를 다시 돌리면 복구된다.
- 전체 랭킹은 순위표를 만들지 않는다(§7 참고용 — 상위 %만).
- 공개 범위 FRIENDS 는 맞팔로우에게만 열리고, 주인 본인은 공개 범위와 무관하게 자기 프로필·카드를 본다. 로그인한 사람이 본 공개 HTML·PNG 는 `Cache-Control: private` + `Vary: Cookie` 로 내보내 공용 캐시가 낯선 사람에게 내주지 않게 한다.
- 팔로우 경로는 존재를 숨긴다: 비공개·친구 공개(맞팔 아님) handle 팔로우는 없는 handle 과 같은 404(팔로우는 기록 — 상대가 맞팔하면 친구), 동시 요청도 같은 404, 언팔로우는 항상 204, 익명은 handle 을 찾기 전에 401. 숨은 팔로우는 내 팔로잉 수·목록·랭킹·피드 어디에도 드러나지 않는다(QA r2 P2-A).
- **남은 위험 — 응답 시간**: 숨은 대상 팔로우는 기록(insert)·공개 범위 조회가 더해져 없는 handle 보다 몇 ms 느리다(조회 경로는 맞췄지만 insert 차이는 남음). 통계로 존재를 추정할 수 있으므로 **게이트웨이(리버스 프록시)에서 `POST /friends/*`·`/u/*`·`/compare/*` 에 사용자·IP 단위 요청 수 제한을 둔다**(권고: 분당 30회 수준). N3 과 같은 성격.

## Docker Compose 로컬 운영

초기 운영 환경은 이 PC 의 Docker Compose 다(`compose.yaml`, 프로젝트 이름 `territory`). 이미지는 루트 `Dockerfile`(Gradle `bootJar` → Spring Boot 레이어 → `eclipse-temurin:21-jre`, 비루트 uid 10001, fontconfig — 카드 한글 글꼴은 sharing 리소스 번들).

| 서비스 | 내용 |
|---|---|
| `mysql` | `mysql:8.4`, utf8mb4 / `utf8mb4_0900_ai_ci`, 서버 시간대 +09:00(앱은 UTC 로 저장), 볼륨 `territory_mysql-data`, 호스트 포트 **미노출** |
| `app` | `SPRING_PROFILES_ACTIVE=prod` 고정(N4), mysql healthy 후 기동 → Flyway V1~V5 + ddl validate, **`127.0.0.1:18080` → 8080**(루프백만), 카드 PNG 볼륨 `territory_share-cards`(`/data/share-cards`), `restart: unless-stopped`, 메모리 1g(힙 75%), 로그 json-file 10MB×5, HEALTHCHECK `/actuator/health` |

### .env (커밋 금지 — `.env.example` 을 복사)

| 키 | 설명 |
|---|---|
| `DB_NAME` | DB 이름(기본 territory) |
| `DB_USERNAME` / `DB_PASSWORD` | 앱 계정(mysql 첫 기동 때 생성). 강한 랜덤 값 |
| `MYSQL_ROOT_PASSWORD` | root(백업·복원 스크립트가 컨테이너 안에서 사용) |
| `TERRITORY_ADMIN_TOKEN` | `/admin/**` 의 `X-Admin-Token`. 강한 랜덤 값 |
| `TERRITORY_PUBLIC_BASE_URL` | 공개 기준 주소(og:image·og:url·OAuth redirect_uri). 로컬은 `http://localhost:18080` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | 선택. 비우면 구글 로그인만 비활성(익명 탐험 정상) |

랜덤 값: `openssl rand -base64 48 | tr -d '/+=\n' | cut -c1-40`. **DB 비밀번호는 mysql 볼륨을 처음 만들 때만 반영**된다 — 나중에 바꾸려면 MySQL 안에서 `ALTER USER` 후 .env 를 맞춘다.

구글 로그인: Google Cloud Console OAuth 클라이언트(웹)의 승인된 리디렉션 URI 에 **`http://localhost:18080/login/oauth2/code/google`**(= `{TERRITORY_PUBLIC_BASE_URL}/login/oauth2/code/google`)를 등록한다. prod 는 세션 쿠키가 `Secure` 라 http 에서는 `localhost` 로 접속해야 한다(브라우저가 localhost 만 예외로 허용 — `127.0.0.1`·LAN IP 로 접속하면 로그인 세션이 유지되지 않는다). 다른 기기·외부 공개는 TLS 리버스 프록시(+ `/u/*`·`/friends/*`·`/compare/*` 요청 수 제한, N3)를 앞에 두고 `TERRITORY_PUBLIC_BASE_URL=https://...` 로 바꾼다.

### 명령

```bash
cp .env.example .env && vi .env                 # 처음 한 번
docker compose up -d --build                    # 기동(처음·업데이트 모두). 끝나면 docker compose ps 로 두 서비스 healthy
docker compose ps                               # 상태
docker compose logs -f app                      # 로그(mysql 은 logs mysql)
curl -s localhost:18080/health                  # {"service":"territory",...}
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:18080/dev/login   # 404 여야 한다(N4)
docker compose stop                             # 중지(데이터 유지) / 다시: docker compose start
docker compose down                             # 컨테이너·네트워크 제거(볼륨 유지)
# docker compose down -v                        # !! 볼륨까지 삭제 = 데이터 전부 삭제 — 운영에서 쓰지 않는다
```

**업데이트(새 코드 배포)**: `git pull` → `./gradlew clean build`(테스트) → `scripts/backup.sh` → `docker compose up -d --build`(app 만 재생성 — Flyway 가 새 마이그레이션 적용) → `docker compose logs app | grep -E "Migrating|Started|ERROR"`. 배포 직후 해야 할 운영 작업(예: 5단계 `POST /admin/rebuild/feed`)은 각 절 참고:
`curl -X POST -H "X-Admin-Token: $TOKEN" localhost:18080/admin/rebuild/feed`.

### 백업·복원

```bash
scripts/backup.sh                               # backups/territory-YYYYmmdd-HHMMSS.sql.gz (최근 14개 유지, BACKUP_KEEP 로 조정)
scripts/restore.sh backups/territory-....sql.gz # app 중지 → DB 를 덤프로 통째 교체 → app 기동(확인 질문, --yes 로 생략)
```

- mysqldump `--single-transaction` 이라 앱을 켠 채로 백업한다. `backups/` 는 gitignore — **다른 디스크·클라우드로 복사**해 둔다(같은 PC 의 볼륨과 함께 잃지 않게). 정기 백업은 cron/launchd 로 `scripts/backup.sh` 를 돌린다.
- 카드 PNG 볼륨은 캐시라 백업하지 않는다(복원 뒤 최대 `share-card.cache-ttl-minutes` 동안 옛 카드가 보일 수 있다).
- DB 에 직접 접속: `docker compose exec mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'`. 호스트 도구가 필요하면 `compose.override.yaml` 에 mysql `ports: ["127.0.0.1:13306:3306"]` 를 넣는다(루프백만).

### E2E 를 MySQL 위에서(compose.e2e.yaml)

```bash
docker compose -p territory-e2e -f compose.yaml -f compose.e2e.yaml up -d --build --wait
cd e2e && E2E_PORT=18090 npx playwright test     # webServer 는 reuseExistingServer 로 컨테이너에 붙는다(global-setup 이 service=territory 확인)
docker compose -p territory-e2e -f compose.yaml -f compose.e2e.yaml down -v
```

`-p territory-e2e` 로 컨테이너·볼륨·네트워크가 운영과 분리된다(포트 `127.0.0.1:18090`, 이미지 태그 `territory-app:e2e`). dev 엔드포인트는 `@Profile("local")` + `territory.dev.enabled=true`(application-local.yml) 일 때만 생기므로, 오버라이드는 **프로파일을 local 로 두고 local yml 의 H2 datasource 만 `SPRING_DATASOURCE_*` 환경변수로 MySQL 로 덮는다**(+ h2-console 끔, 관리자 토큰 `local-admin-token`, 공개 주소 `http://localhost:18090`). jar 에 새 프로파일 파일을 추가하지 않았으므로 운영 compose(prod 고정)에서 dev 가 켜질 경로는 없다. **운영 프로젝트(`territory`)에 compose.e2e.yaml 을 겹쳐 쓰지 말 것** — `-p territory-e2e` 없이 쓰면 compose.e2e.yaml 의 `name: territory-e2e` 가 적용되지만, 명시하는 습관을 둔다.
