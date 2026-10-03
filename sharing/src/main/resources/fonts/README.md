# 카드 렌더러 글꼴 (SIL Open Font License 1.1)

자랑 카드 PNG(Java2D)가 한글을 깨지지 않게 그리도록 넣은 글꼴. 모두 OFL 1.1 — 재배포·임베딩 허용, 글꼴 단독 판매 금지,
라이선스 전문을 함께 둔다(같은 폴더 `OFL-*.txt`). 수정하지 않은 원본 파일이다.

| 파일 | 글꼴 | 저작권 | 출처 | 라이선스 |
|------|------|--------|------|----------|
| `DoHyeon-Regular.ttf` | Do Hyeon (카드 제목·큰 숫자 — 프로토타입 디스플레이 글꼴) | Copyright 2018 The Do Hyeon Project Authors | https://github.com/google/fonts/tree/main/ofl/dohyeon | `OFL-DoHyeon.txt` |
| `NanumGothic-Regular.ttf`, `NanumGothic-Bold.ttf` | Nanum Gothic (본문·문장부호 폴백) | Copyright (c) 2010, NHN Corporation | https://github.com/google/fonts/tree/main/ofl/nanumgothic | `OFL-NanumGothic.txt` |

리소스를 읽지 못하면 렌더러(`CardFonts`)가 한글을 그릴 수 있는 시스템 글꼴로, 그것도 없으면 SansSerif 로 내려가며 WARN 을 남긴다.
