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
