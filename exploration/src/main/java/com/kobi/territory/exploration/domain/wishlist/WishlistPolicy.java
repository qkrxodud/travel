package com.kobi.territory.exploration.domain.wishlist;

/**
 * 가고 싶은 곳 규칙 값. domain 은 설정을 모르므로 application 이 territory.wishlist.max-pins 로 만들어 넘긴다.
 *
 * @param maxPins 아직 다녀오지 않은 핀의 최대 수
 */
public record WishlistPolicy(int maxPins) {
    public WishlistPolicy {
        if (maxPins < 1) throw new IllegalArgumentException("maxPins >= 1: " + maxPins);
    }
}
