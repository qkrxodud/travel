/**
 * 분석 인프라(JDBC). 분석은 많이 쓰고(원본 이벤트 묶음 insert) 크게 센다(구간 COUNT DISTINCT·GROUP BY) — 그래서 JPA 엔티티 대신
 * JdbcTemplate 를 쓴다(배치 insert, "없으면 넣기"·"비어 있을 때만 채우기", 집계 질의). {@code entity}: 테이블 한 줄 ↔ 도메인 변환
 * ({@code XxxRow} — 한 테이블 한 파일, 변환은 그 줄이 가진다) · {@code repository}: 도메인 포트를 구현한 어댑터("어떻게 저장할지"만).
 * 시각은 UTC 의 DATETIME(6) 으로(다른 테이블의 Hibernate 설정과 같다), 날짜는 서울 날짜를 DATE 로 둔다.
 * 같은 컨텍스트 infra 밖에서는 참조하지 않는다(ArchUnit).
 */
package com.kobi.territory.analytics.infra;
