package com.kobi.territory.analytics.domain.metrics;

/**
 * 하루 공개 페이지 열람 수.
 *
 * @param profileViews 공개 프로필 열람(사람)
 * @param cardViews    자랑 카드·VS 카드 이미지 열람(사람)
 * @param botViews     링크 미리보기·크롤러 등 봇의 열람(사람 수에서 뺀 것 — "링크가 공유됐다"는 신호로만 본다)
 */
public record PageViews(int profileViews, int cardViews, int botViews) {

    public static final PageViews NONE = new PageViews(0, 0, 0);
}
