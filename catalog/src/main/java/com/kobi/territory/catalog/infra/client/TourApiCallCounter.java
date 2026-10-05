package com.kobi.territory.catalog.infra.client;

import java.time.LocalDate;

/**
 * TourAPI 하루 호출 수(tourapi_usage — 서비스 시간대 날짜별, 재기동해도 이어진다). 상한 판단은 DB 한 문장(조건부 증가)이라 여러 요청이 동시에 와도
 * 상한을 넘지 않는다.
 */
public interface TourApiCallCounter {

    /** 그날 호출 수가 상한보다 작으면 하나 세고 true, 아니면 세지 않고 false. */
    boolean tryAcquire(LocalDate day, int dailyLimit);

    int usedOn(LocalDate day);
}
