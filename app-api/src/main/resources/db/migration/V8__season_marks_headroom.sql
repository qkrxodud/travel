-- 9단계 QA P3-9 — 계절 회차의 센 방문(season_progress.marks, "KR-xxxxx|explorerId" 쉼표 구분)에 여유를 둔다.
-- 지금 규칙(지도 멤버 ≤ 4 × 회차 10곳 × 46자)이면 최대 1,840자로 VARCHAR(2000) 의 여유가 8% 뿐이라, 멤버 상한·회차 지역 수가 늘면 넘친다
-- (넘치면 도감 구독자가 계속 실패한다). V7 은 커밋된 마이그레이션 규칙상 고치지 않고 여기서 넓힌다(utf8mb4 32,000바이트 — 행 크기 한도 안).
ALTER TABLE season_progress MODIFY COLUMN marks VARCHAR(8000) NOT NULL;
