package com.kobi.territory.catalog.domain.lineup;

import java.time.LocalDate;

/** 포트: 바깥 계절 자료(한국관광공사 TourAPI — 행사정보조회·키워드 검색 관광지). 구현은 infra/client. */
public interface FestivalSource {

    /** 서비스 키가 있는지 — 없으면 아무 호출도 하지 않는다(지금과 같은 동작). */
    boolean configured();

    /**
     * [from, until] 날짜 범위에 열리는 축제. 실패해도 예외 대신 {@link FestivalFetch.Failed}.
     *
     * @param bypassCache 응답 캐시를 건너뛰고 새로 읽는다(관리자 즉시 확인)
     */
    FestivalFetch festivalsBetween(LocalDate from, LocalDate until, boolean bypassCache);

    /**
     * 이름에 키워드가 든 관광지(키워드 검색, 관광지 유형만). 실패해도 예외 대신 {@link FestivalFetch.Failed}. 결과는 {@link FestivalFetch.Fetched#attractions}.
     */
    FestivalFetch attractionsMatching(String keyword, boolean bypassCache);

    /** 오늘 호출 수·한도(관리자 화면). */
    FetchUsage usage();
}
