package com.kobi.territory.sharing.domain.card;

import java.time.Duration;
import java.util.Objects;

/**
 * 카드 캐시 정책: 한 번 그린 카드는 원천이 바뀌어도 최소 minTtl 동안은 그대로 낸다(설정 territory.share-card.cache-ttl-minutes,
 * 기본 10분 — 체크인이 몰려도 링크가 열릴 때마다 다시 그리지 않게). 원천이 그대로면 TTL 이 지나도 다시 그리지 않는다.
 */
public record CardCachePolicy(Duration minTtl) {
    public CardCachePolicy {
        Objects.requireNonNull(minTtl, "minTtl");
        if (minTtl.isNegative()) throw new IllegalArgumentException("minTtl=" + minTtl);
    }
}
