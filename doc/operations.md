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

## 9단계 계절 한정 테마·재방문 도장·가고 싶은 곳

- **연례 작업 — 계절 회차 배경 아이템 추가(리더 결정 Q2)**: 회차 배경은 V7 에 `season:autumn-2026` ~ `season:autumn-2030`(봄·가을 9개)만 이관돼 있다. **2031 회차부터는 매년 봄 회차(3/20) 전에** 그 해 두 회차의 배경을 운영 API 로 넣는다. 정의가 없는 회차를 완성하면 XP·칭호만 받고 배경은 없다(소급 지급 없음 — 정의 생성 뒤의 완성에만 준다). `season:` 접두어는 이관 전용이라 운영 추가는 `event:` 를 쓴다:

  ```http
  POST /admin/items
  X-Admin-Token: <territory.admin.token>
  Content-Type: application/json

  {"itemId": "event:season-spring-2031", "name": "2031 벚꽃 명소 배경", "emoji": "🌸", "slot": "BG", "tier": "LEGEND",
   "theme": "blossom", "grantRule": "SEASON_COMPLETE", "grantRef": "spring-2031"}
  ```
  가을은 `"itemId": "event:season-autumn-2031"`, `"name": "2031 단풍 명소 배경"`, `"emoji": "🍁"`, `"theme": "autumn"`, `"grantRef": "autumn-2031"`. `grantRef` 는 `seasons.json` 에 있는 계절의 `{계절}-{연도}` 여야 한다(아니면 400 UNKNOWN_ITEM_REFERENCE). 계절 정의(기간·지역)를 바꾸면 재계산 영향(닫힌 회차는 확정 기록이라 다시 세지 않는다)을 확인한다.
- **추후 과제(리더 결정 Q3)**: 계절 화면은 지금 개인 지도 기준이다 — 공유 지도에서 함께 완성한 회차는 `GET /seasons/current?mapId=` 로는 보이지만 화면의 개인 지도 배지에는 나오지 않는다. 공유 지도 회차 표시는 후속 단계에서 다룬다.
- 새 구독 타입(SeasonCompleted·RevisitStamped → social.feed 등)이 생겼다 — 9단계 배포 직후 `POST /admin/rebuild/feed` 한 번(5단계 절차와 같다).

## 10단계 분석 이벤트(자체 수집·익명)

- **비밀값 `TERRITORY_ANALYTICS_SALT`(필수)**: 분석 저장소에는 탐험가 id 대신 이 값을 섞은 HMAC-SHA256 해시만 남는다. 없으면 기동 실패. **한 번 정하면 바꾸지 않는다** — 바꾸면 그때부터 같은 탐험가를 다른 사람으로 세어 코호트·리텐션·퍼널이 끊긴다(원본은 90일 뒤 사라지므로 되돌릴 수도 없다). 미스터리 비밀값과 다른 값을 쓴다.
- **무엇을 남기나**: 원본 `analytics_event`(이벤트 이름·서울 날짜·행위자 열쇠(탐험가 해시 또는 `v:`+브라우저 무작위 방문 ID)·기기 유형(MOBILE/TABLET/DESKTOP/BOT/UNKNOWN)·나라(앞단 `CF-IPCountry` 헤더가 있을 때만)·검증된 짧은 필드). **IP·User-Agent·handle·메모·토큰 원문은 저장하지 않는다**(IP 는 레이트 리밋 판단에만 메모리에서 쓴다). 방문·여정(`analytics_visitor`·`analytics_explorer`)과 집계(`analytics_daily`·`analytics_daily_breakdown`·`analytics_cohort`)는 남는다.
- **보관**: 원본은 90일(`territory.analytics.retention-days`). 일 배치(`territory.analytics.batch-cron`, 기본 매일 04:10 서울)가 지난 원본을 5,000줄씩 나눠 지우고, **마지막 활동이 보관 기간보다 오래된 방문·여정**(`analytics_visitor`·`analytics_explorer` — 90일 전보다 먼저 처음 봤고/가입했고 남은 원본이 하나도 없는 것)도 지운다. 그 사람이 다시 오면 새 방문으로 세고, 여정은 가입일을 모르는 채로 다시 시작해 코호트에는 들어가지 않는다(이미 저장된 집계는 그대로). 집계(`analytics_daily`·`_breakdown`·`analytics_cohort`)는 지우지 않는다.
- **배치가 채우는 범위**: 원본이 남아 있는 지난 90일 전부 — 하루 지표는 최근 3일 + 아직 계산하지 않은 날, 코호트는 최근 35일 + 아직 계산하지 않은 날. 그래서 `GET /admin/metrics?days=90` 의 `missingDays` 는 배치를 한 번 돌리면 비고, 원본 보관 기간이 지나 다시 셀 수 없는 날은 `expiredDays` 로 따로 나온다. 수동 실행 `POST /admin/metrics/batch`(X-Admin-Token). 배포 첫날의 첫 배치는 90일을 다 계산한다(원본 30만 줄 기준 MySQL 약 28초, 이후 매일 약 4~5초 — 10단계 MySQL 측정). 배치가 오래 멈췄다가 늦게 채운 옛날 날은 그날의 30일 구간 앞부분 원본이 이미 지워졌을 수 있어 MAU·K 계수가 작게 나올 수 있다.
- **지표 보기**: `GET /admin/metrics?days=30`(X-Admin-Token, 1~90일) — JSON. 지난 날은 배치 값, 오늘은 실시간(60초 재사용). 정의는 `_workspace/10_contracts.md` §3. 화면은 관리자 페이지(프론트).
- **레이트 리밋과 요청 주소**: 방문 ID 마다 몰아서 20번·분당 30번, 요청 주소마다 몰아서 60번·분당 120번(인스턴스 메모리). 요청 주소는 **실제 접속 주소**다 — 클라이언트가 보낸 `X-Forwarded-For`·`CF-Connecting-IP` 는 믿지 않는다(바꿔 보내 상한을 피할 수 있어서, 10단계 QA P2-2). 믿는 프록시 `TERRITORY_TRUSTED_PROXIES`(CIDR, 쉼표, 기본 비어 있음)에서 온 요청만 `CF-Connecting-IP` → 없으면 `X-Forwarded-For` 를 오른쪽부터 보며 믿는 프록시를 건너뛴 첫 주소를 쓴다. 나라 헤더 `CF-IPCountry` 도 믿는 프록시를 거쳤을 때만 쓴다.
  - **Cloudflare Tunnel 을 붙일 때**: `cloudflared` 를 compose 의 같은 네트워크에 서비스로 두고(외부 포트 없음), 그 네트워크 대역을 믿는다 — 예: compose 네트워크를 `networks: default: ipam: config: [{subnet: 172.30.0.0/24}]` 로 고정하고 `.env` 에 `TERRITORY_TRUSTED_PROXIES=172.30.0.0/24`. 이때 호스트의 `127.0.0.1:18080` 으로 직접 들어오는 요청은 도커 게이트웨이 주소(같은 대역의 .1)로 보이므로, 대역 대신 cloudflared 컨테이너 주소 하나(`ipv4_address` 로 고정, 예 `172.30.0.10/32`)만 믿는 편이 더 좁다. Cloudflare 가 아닌 리버스 프록시(nginx 등)를 쓰면 그 프록시 주소를 넣고 프록시가 `X-Forwarded-For` 에 접속 주소를 덧붙이게 한다.
  - **여러 대로 늘리면** 인스턴스마다 따로 세어 상한이 대수만큼 커진다 — 그때는 공유 저장소(Redis 등)로 옮기거나 앞단 프록시의 요청 수 제한을 함께 쓴다.
- **운영 비밀값 강도**: prod 프로파일은 `TERRITORY_ADMIN_TOKEN`·`TERRITORY_MYSTERY_SALT`·`TERRITORY_ANALYTICS_SALT` 가 비었거나 16자 미만이거나 `change-me`·`local-`·`example` 같은 자리표시자를 담으면 기동하지 않는다(`ProductionSecrets` — 어느 값인지만 알리고 값은 로그에 남기지 않는다). 12단계부터 **분석 비밀값이 미스터리 비밀값과 같아도 기동하지 않는다**(10단계 QA r2 P3-a — 분석 값은 바꾸면 코호트가 끊기므로, 같다면 미스터리 값을 바꾼다. 미스터리 값을 바꾸면 아직 기록되지 않은 주부터 다른 지역이 된다). 랜덤 값: `openssl rand -base64 48 | tr -d '/+=\n' | cut -c1-40`.
- **지표 응답의 신뢰도 표시(12단계 추가, 10단계 QA r2 P3-b·P3-c)**: `pendingCohortDays` — 배치가 아직 퍼널·리텐션을 계산하지 않은 코호트 날(원본이 남아 있어 배치로 채울 수 있다, 화면은 표에 "배치 전" 줄). `daily[].partialWindow` — 그날 하루 지표를 계산할 때 30일 구간(MAU·K 계수) 앞부분 원본이 이미 보관 기간을 지나 지워졌다(61일보다 오래된 빈 날을 늦게 채운 경우 — 값이 작게 나오므로 화면은 "신뢰도 낮음"). 배치가 매일 돌면 생기지 않는다.
- **믿는 프록시를 빠뜨렸을 때의 경고(12단계 추가, 10단계 QA r2 P3-d)**: 사설·루프백 주소(같은 호스트·compose 네트워크의 프록시로 보임)에서 프록시 헤더(`CF-Connecting-IP`·`X-Forwarded-For`)가 왔는데 그 주소를 믿지 않으면 WARN `분석 수집: 프록시 헤더 … 믿는 프록시가 아니다` 를 1시간에 한 번 남긴다. 이 로그가 보이면 `TERRITORY_TRUSTED_PROXIES` 를 아래 배포 체크리스트대로 맞춘다 — 그대로 두면 모든 방문자가 프록시 주소 하나로 세져 사용자가 늘 때 수집이 조용히 429 로 버려진다.
- **CSRF**: `POST /events` 는 위조 방지 토큰 검사에서 빠진다(페이지를 닫을 때 `navigator.sendBeacon` 은 헤더를 못 붙인다). `application/json` 본문만 받아 다른 사이트 폼으로는 보낼 수 없고, 위조해도 분석 줄 하나가 늘 뿐 게임 상태는 바뀌지 않는다.
- **봇**: 링크 미리보기(카카오톡·페이스북·슬랙 등)·크롤러(다음 `Daumoa` 등)·curl 은 User-Agent 로 거칠게 가른다 — 화면 이벤트는 버리고, 공개 카드 열람은 `botViews` 로 따로 센다. 다음 앱 안의 브라우저(`DaumApps`)는 사람으로 센다.
- **보호권 사용·지급은 아직 안 센다** — 진행 컨텍스트에 공개 이벤트가 없다(추가되면 분석 구독자에 한 줄 더한다).
- 배포 전 이벤트(분석을 켜기 전 가입·체크인)는 소급하지 않는다 — 그 탐험가는 가입일을 모르는 여정으로 시작해 리텐션 코호트·첫 체크인 퍼널에 들어가지 않는다.

## 12단계 웹 푸시 알림(PWA)

- **VAPID 키(필수)** — 서버가 브라우저 푸시 서비스(FCM·Mozilla·Apple·Windows)에 "구독을 만든 그 서버"임을 증명하는 P-256 키 쌍. `.env` 의 `TERRITORY_VAPID_PUBLIC_KEY`(비압축 점 65바이트 base64url)·`TERRITORY_VAPID_PRIVATE_KEY`(32바이트 base64url)·`TERRITORY_VAPID_SUBJECT`(`mailto:운영자@도메인` 또는 `https://도메인` — 푸시 서비스가 문제 있을 때 연락하는 곳). 없으면 compose 가 멈추고, prod 는 형식이 틀리거나 **한 쌍이 아니거나**(기동할 때 서명 → 검증) 로컬 시험용 키·예시 주소면 기동하지 않는다(값은 로그에 남기지 않는다). 만드는 법(macOS LibreSSL·OpenSSL 모두):

  ```bash
  openssl ecparam -name prime256v1 -genkey -noout -out vapid.pem          # 이 파일은 커밋하지 말고 안전하게 보관
  echo "TERRITORY_VAPID_PRIVATE_KEY=$(openssl ec -in vapid.pem -outform DER 2>/dev/null | tail -c +8 | head -c 32 | base64 | tr '/+' '_-' | tr -d '=\n')"
  echo "TERRITORY_VAPID_PUBLIC_KEY=$(openssl ec -in vapid.pem -pubout -outform DER 2>/dev/null | tail -c 65 | base64 | tr '/+' '_-' | tr -d '=\n')"
  ```
  (Node 가 있으면 `npx web-push generate-vapid-keys` 도 같은 형식.) **한 번 정하면 바꾸지 않는다** — 바꾸면 모든 브라우저 구독이 이전 공개 키에 묶여 있어 보내기가 거절(403)되고, 사용자가 앱을 다시 열어 구독을 새로 보낼 때까지 알림이 끊긴다. local·E2E 는 `application-local.yml` 의 시험용 키(공개된 값)를 쓴다.
- **HTTPS 필수**: 서비스워커·푸시 구독은 보안 출처에서만 된다 — 로컬은 `http://localhost` 예외, 그 밖에는 Cloudflare Tunnel(또는 TLS 리버스 프록시) 뒤의 `https://` 도메인이어야 한다(`TERRITORY_PUBLIC_BASE_URL` 도 https). iOS Safari 는 홈 화면에 추가한(설치한) PWA 에서만 웹 푸시를 받는다.
- **구독 주소 허용 목록(SSRF 방지)**: 서버가 구독 주소로 직접 POST 하므로 알려진 푸시 서비스 호스트만 받는다(`territory.push.allowed-hosts` — `fcm.googleapis.com`, `updates.push.services.mozilla.com`, `*.push.services.mozilla.com`, `*.push.apple.com`, `*.notify.windows.com`, https 만). 다른 브라우저가 거절되면(400 `PUSH_ENDPOINT_NOT_ALLOWED`) 그 호스트를 확인해 이 설정에 더한다. 리디렉션은 따라가지 않는다. local 만 `allow-localhost` 로 `localhost`·`127.0.0.1`(http 포함 — 개발용 가짜 푸시 서비스).
- **알림 3종(cron, 서울 시간, `-` 면 그 알림 끔)**: 계절 테마 시작일 `territory.push.schedule.season-cron`(기본 매일 08:30, 시작일에만) · 이번 주 미스터리 `mystery-cron`(월 09:00, 지역 이름은 감춤) · 스트릭 지키기 `streak-cron`(매일 19:00, 그 달 마지막 날 − `streak-days-before-month-end`(3)일에만 — 지난달까지 이어 왔는데 이번 달 새 지역이 없는 사람, 보호권 수 안내). 같은 날 겹치면 먼저 도는 알림이 이긴다(계절 → 미스터리 → 스트릭).
- **규칙**: 한 사람 **하루(서울 날짜) 최대 `daily-limit`(1)개** — 발송 계획은 받는 사람 루트 잠금 뒤 그날 기록을 세고(MySQL 동시성 테스트), 보내지 못하고 닫힌 알림(만료·실패·취소)은 세지 않는다. **조용한 시간 22:00~08:00**(`quiet-hours`) 에는 보내지 않고 08:00 으로 미룬다. 기기가 없거나(동의 안 함·모두 해지) 그 종류를 끈 사람에게는 계획하지 않고, 보내기 직전에도 다시 본다. 멱등 열쇠 = 탐험가·종류·기간(UNIQUE) — 스케줄이 두 번 돌거나 재시작해도 한 번.
- **발송·재시도·만료**: 발송기(`territory.push.dispatch.delay-ms`, 기본 30초)가 보낼 때가 된 기록을 100개씩 잡아 기기마다 보낸다(초당 `max-per-second` 20, 요청당 `send-timeout` 10초, TTL 12시간). 응답 404·410 은 그 기기를 지운다(다시 구독하면 다시 생긴다). 429·5xx·연결 실패는 5분 → 10분 → 20분 … 최대 1시간(Retry-After 가 더 길면 그만큼)으로 4번까지 — **그날 안·조용한 시간 전까지만**, 넘으면 만료(EXPIRED, 다음 날 늦게 보내지 않는다). 400·401·403·413 은 다시 보내지 않는다(403 이 많으면 VAPID 키가 바뀐 것). 한 기기라도 받으면 SENT(다른 기기의 일시 실패는 다시 보내지 않음).
- **대량 발송·단일 인스턴스 가정**: 받을 사람을 500명씩 불러와 한 사람씩 짧은 트랜잭션으로 계획하고, 속도 제한은 이 인스턴스 안에서만 지킨다(1만 명 × 1기기 ≈ 초당 20건으로 약 8분 — 조용한 시간 전에 끝나도록 cron 을 늦은 밤에 두지 않는다). 발송기는 기록을 낙관적 잠금으로 잡아 같은 기록을 두 발송기가 동시에 보내지 않지만, 보내는 중에 죽은 기록은 `claim-timeout`(10분) 뒤 다시 보낸다(최소 1회 — 기기에서는 같은 tag 라 하나로 겹친다). 여러 대로 늘리면 속도 제한이 대수만큼 커진다.
- **계정 병합**: 익명으로 알림을 켠 브라우저가 로그인으로 계정에 병합되면 그 기기는 계정 탐험가로 옮겨 간다(구독자 `notification.recipient`, 기기 수 상한 5 — 넘으면 오래된 기기부터). 같은 브라우저를 다른 탐험가가 구독하면 그 탐험가 것이 된다.
- **분석**: 서버가 보낸 알림은 `push_sent`(kind·기기 수), 화면은 알림을 눌러 열면 `push_open`(kind)·`app_open(entry=push)` 를 보낸다(클릭 경로에 `?from=push&push=종류`). 동의율은 `push_prompt`.
- **local 확인**: `POST /dev/push/send {"kind":"mystery"|"streak"|"season","force":true}`(기본 force — 날짜 조건·조용한 시간 건너뜀, `force:false` 면 `/dev/clock` 으로 그날로 밀어서 스케줄과 같게), `GET /dev/push/deliveries`(내 발송 기록), 가짜 푸시 서비스 `POST·GET /dev/push/inbox/{상자}`(구독 주소 `http://localhost:포트/dev/push/inbox/상자`, `gone…` 410·`busy…` 429·`reject…` 403). 실제 브라우저 수신은 화면 쪽 E2E.

## 배포 체크리스트(12단계 정리)

배포·업데이트 전에 확인한다(위 단계별 절의 요약).

1. `.env` 에 필수 값: `DB_*`·`MYSQL_ROOT_PASSWORD`, `TERRITORY_ADMIN_TOKEN`·`TERRITORY_MYSTERY_SALT`·`TERRITORY_ANALYTICS_SALT`(서로 다른 16자 이상 랜덤), `TERRITORY_VAPID_PUBLIC_KEY`·`TERRITORY_VAPID_PRIVATE_KEY`·`TERRITORY_VAPID_SUBJECT`(위 12단계), `TERRITORY_PUBLIC_BASE_URL`(도메인을 붙이면 https).
2. 운영 프로파일: 컨테이너는 `SPRING_PROFILES_ACTIVE=prod` 고정 — 기동 뒤 `/dev/login` 이 404 인지 본다(N4).
3. **앞단 프록시(Cloudflare Tunnel·nginx)를 붙였다면 `TERRITORY_TRUSTED_PROXIES` 를 그 프록시 주소로 반드시 정한다**(10단계 절의 예시 — cloudflared 컨테이너 주소 하나 `/32` 권장). 비워 두면 모든 방문자가 프록시 주소 하나로 세져 분석 수집이 429 로 버려진다. 기동 뒤 로그에 `분석 수집: 프록시 헤더 … 믿는 프록시가 아니다` WARN 이 없어야 한다. 프록시 없이 루프백 포트로만 쓰면 비워 둔다.
4. 웹 푸시는 https 출처에서만 — Tunnel 도메인으로 열어 알림 켜기 → `GET /push/preferences` 의 `devices` 가 1 이상인지 본다.
5. 업데이트 직전 백업(`scripts/backup.sh`)과 롤백 이미지 태그(아래 "명령").
6. 새 구독 타입이 생긴 단계면 배포 직후 `POST /admin/rebuild/feed` 한 번(5·9단계 절).

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
| `TERRITORY_ADMIN_TOKEN` | `/admin/**` 의 `X-Admin-Token`. 강한 랜덤 값(16자 이상, 자리표시자 금지 — 10단계부터 prod 기동 검사) |
| `TERRITORY_MYSTERY_SALT` | 이번 주 미스터리 지역 주차 시드에 섞는 서버 비밀값(8단계, 필수 — 없으면 기동 실패). 강한 랜덤 값. 바꾸면 아직 기록되지 않은 주부터 다른 지역이 되고, 이미 기록된 주(`mystery_week`)는 그대로 |
| `TERRITORY_ANALYTICS_SALT` | 분석 저장소의 탐험가 해시에 섞는 서버 비밀값(10단계, 필수 — 없으면 기동 실패). 강한 랜덤 값, 미스터리 비밀값과 다르게. **바꾸지 않는다**(바꾸면 코호트·리텐션이 끊긴다) |
| `TERRITORY_VAPID_PUBLIC_KEY` / `TERRITORY_VAPID_PRIVATE_KEY` | 웹 푸시 VAPID 키 쌍(12단계, 필수 — 만드는 법은 위 12단계 절). **바꾸지 않는다**(바꾸면 모든 구독 무효). 로컬 시험용 키면 prod 기동 실패 |
| `TERRITORY_VAPID_SUBJECT` | 웹 푸시 연락처 `mailto:…` 또는 `https://…`(12단계, 필수). 예시 주소면 prod 기동 실패 |
| `TERRITORY_TRUSTED_PROXIES` | 선택(10단계). 분석 레이트 리밋이 믿는 프록시 CIDR(쉼표). 비우면 아무도 믿지 않음 — **Cloudflare Tunnel·리버스 프록시를 붙였다면 반드시 그 주소로**(위 10단계 절·배포 체크리스트 3) |
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

**업데이트(새 코드 배포)**: `git pull` → `./gradlew clean build`(테스트 — 웹 프론트 `npm ci`·`npm run check`·`npm run build` 포함) → `scripts/backup.sh` → **지금 이미지를 `prev` 로 태깅**(아래) → `docker compose up -d --build`(app 만 재생성 — Flyway 가 새 마이그레이션 적용, 이미지 빌드 스테이지가 프론트도 빌드) → `docker compose logs app | grep -E "Migrating|Started|ERROR"` → 브라우저로 `http://localhost:18080/` 를 열어 화면(지도 칠해짐·탭 전환)을 한 번 확인. 배포 직후 해야 할 운영 작업(예: 5단계 `POST /admin/rebuild/feed`)은 각 절 참고:
`curl -X POST -H "X-Admin-Token: $TOKEN" localhost:18080/admin/rebuild/feed`.

```bash
# 업데이트 직전(빌드 전에) — compose 는 territory-app:latest 를 --build 로 덮어쓰므로 지금 돌고 있는 이미지에 이름을 하나 더 붙여 둔다
docker image inspect territory-app:latest > /dev/null 2>&1 && docker tag territory-app:latest territory-app:prev   # 첫 배포면 건너뛴다
scripts/backup.sh                               # 같은 시점의 DB 덤프(마이그레이션이 있는 배포를 되돌릴 때 필요)
docker compose up -d --build
```

**롤백(직전 이미지로 되돌리기)**: 새 버전에서 화면·API 문제가 나면 코드를 고치기 전에 먼저 되돌린다.

```bash
docker tag territory-app:prev territory-app:latest   # latest 를 직전 이미지로 되돌린다
docker compose up -d --no-build app                  # 다시 빌드하지 않고 그 이미지로 app 만 재생성
docker compose ps && curl -s localhost:18080/health  # healthy · {"service":"territory",...}
docker image ls territory-app                        # latest 와 prev 가 같은 IMAGE ID 인지 확인
```

- 되돌린 배포가 **새 Flyway 마이그레이션을 적용했다면** 옛 이미지는 모르는 마이그레이션을 건너뛰고(Flyway 기본 `*:future` 무시) 뜨지만, 스키마가 옛 코드와 맞지 않을 수 있다. 그때는 업데이트 직전 덤프로 DB 도 되돌린다: `scripts/restore.sh backups/territory-<업데이트 직전>.sql.gz`(app 중지 → 복원 → app 기동). 덤프 이후에 들어온 방문 기록은 사라지므로 롤백 시각을 기록해 둔다.
- 문제를 고친 새 버전을 다시 배포할 때도 위 업데이트 절차(태깅 → 빌드)를 그대로 밟는다. `prev` 는 한 단계만 남는다 — 더 오래 남기려면 `territory-app:<날짜>` 처럼 태그를 하나 더 붙인다.

### 웹 프론트(React, 6단계)

- 화면은 `frontend/`(Vite + React + TypeScript) 다. 산출물(`frontend/dist`)은 git 에 없고, `./gradlew build`·`bootRun`·`docker build` 가 매번 만든다(`processResources` 가 `static/` 으로 복사). Node 는 Gradle node 플러그인이 받는다(로컬 `.gradle/nodejs`, 이미지 빌드 스테이지 안) — 호스트·이미지에 Node 를 설치하지 않는다.
- 이미지 빌드에 npm 레지스트리·nodejs.org 접근이 필요하다(의존성 레이어는 `frontend/package-lock.json` 이 그대로면 캐시). 오프라인 빌드가 필요하면 미리 받은 레이어 캐시를 쓴다.
- 화면 문제 롤백: 직전 이미지로 되돌리는 것이 기본이다(위 "롤백" — 업데이트 전에 `territory-app:prev` 태깅). React 이전 전 화면(바닐라 JS)은 비교·긴급 롤백용으로 `doc/legacy-index.html` 에 보존 — `app-api/src/main/resources/static/index.html` 로 되돌려 넣고 app-api `build.gradle` 의 `processResources { from(frontendBuild) … }` 를 빼면 예전 화면으로 빌드된다(서버 API 는 그대로 호환).
- 프론트만 고칠 때: Spring 을 빈 포트로 띄우고(`./gradlew :app-api:bootRun --args='--server.port=18081'`) `cd frontend && npm run dev`. Vite 프록시 기본 대상은 `http://localhost:18081` 이고 다른 포트면 `VITE_API_TARGET=http://localhost:18082 npm run dev` 로 바꾼다(API 경로는 `vite.config.ts` 의 `API_PATHS` 한 곳에서 Spring 으로 프록시). 기본값을 bootRun 기본 포트 8080 으로 두지 않은 것은 이 PC 에서 8080 을 다른 앱이 쓰기 때문이다(env 없이 띄우면 남의 앱으로 프록시된다).

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
