/**
 * 분석 공개 창구 — {@code api.web} 만 있다: 화면 이벤트 수집 {@code POST /events}, 운영 지표 {@code GET /admin/metrics}, 공개 페이지
 * 열람을 적는 요청 필터. 공개 이벤트(api.event)·조회 계약(api.query)은 없다 — 분석은 관찰자라 아무 컨텍스트도 분석을 참조하지 않는다(ArchUnit).
 */
package com.kobi.territory.analytics.api;
