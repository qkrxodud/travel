package com.kobi.territory.sharing.application;

import com.kobi.territory.sharing.domain.card.CardCachePolicy;
import java.time.Duration;

/**
 * 공유 설정값(app-api 가 territory.* 를 바인딩해 넘긴다 — 모듈은 TerritoryProperties 를 모른다).
 *
 * @param cardMinTtl    카드 최소 캐시 시간(territory.share-card.cache-ttl-minutes)
 * @param publicBaseUrl 공개 기준 주소(territory.public-base-url, 예: https://territory.kr) — og:image·og:url 절대 주소의 앞부분.
 *                      요청 Host 헤더를 믿지 않는다(QA P3-2). 끝의 '/' 는 뗀다
 */
public record SharingSettings(Duration cardMinTtl, String publicBaseUrl) {
    public SharingSettings {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) throw new IllegalArgumentException("territory.public-base-url 필요");
        publicBaseUrl = publicBaseUrl.strip().replaceAll("/+$", "");
    }

    public CardCachePolicy cachePolicy() {
        return new CardCachePolicy(cardMinTtl);
    }
}
