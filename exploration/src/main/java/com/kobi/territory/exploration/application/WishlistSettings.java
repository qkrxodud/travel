package com.kobi.territory.exploration.application;

import com.kobi.territory.exploration.domain.wishlist.WishlistPolicy;

/**
 * 가고 싶은 곳 규칙 값(9단계) — app-api 가 territory.wishlist.max-pins 로 만든다(기본 30).
 *
 * @param maxPins 아직 다녀오지 않은 핀의 최대 수
 */
public record WishlistSettings(int maxPins) {
    public WishlistPolicy policy() {
        return new WishlistPolicy(maxPins);
    }
}
