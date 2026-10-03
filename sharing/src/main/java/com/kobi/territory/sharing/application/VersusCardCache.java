package com.kobi.territory.sharing.application;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * VS 카드 메모리 캐시(QA P3-9) — 인증 없는 공개 경로라 공개 탐험가 쌍마다 파일·행을 남기지 않는다. 키 = 두 요약의 해시(그래서
 * 내용이 바뀌면 자연히 새 키), 최대 {@link #MAX_ENTRIES}장 LRU + 최소 TTL 만료. 인스턴스마다 따로(다중 인스턴스면 각자 그린다).
 */
final class VersusCardCache {

    static final int MAX_ENTRIES = 128;

    private final Duration ttl;
    private final Map<String, CardImage> entries = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CardImage> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    VersusCardCache(Duration ttl) {
        this.ttl = ttl;
    }

    synchronized Optional<CardImage> find(String key, Instant now) {
        CardImage image = entries.get(key);
        if (image == null) return Optional.empty();
        if (!ttl.isZero() && now.isAfter(image.renderedAt().plus(ttl))) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(image);
    }

    synchronized void put(String key, CardImage image) {
        entries.put(key, image);
    }

    synchronized int size() {
        return entries.size();
    }
}
