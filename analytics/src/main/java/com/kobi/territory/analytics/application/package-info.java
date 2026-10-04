/**
 * 분석 유스케이스(얇은 서비스 — 불러와서 → 도메인에 시키고 → 저장). 화면 이벤트 수집, 서버 사실 기록(outbox 구독자
 * {@code analytics.events}), 공개 페이지 열람 기록, 일 배치(지표 계산·원본 삭제), 지표 조회, local 시드.
 * 분석은 관찰자다 — 다른 컨텍스트의 공개 이벤트(api.event)만 받고, 아무것도 내보내지 않는다.
 */
package com.kobi.territory.analytics.application;
