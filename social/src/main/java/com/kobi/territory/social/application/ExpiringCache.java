package com.kobi.territory.social.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 애플리케이션 캐시(인스턴스 메모리, 설정 TTL). 일 1회 배치 스냅숏(상위 %·지역 통계)처럼 드물게 바뀌는 값을 DB 왕복 없이 낸다 —
 * Redis 는 트래픽이 생긴 뒤에 도입한다(문서화). 배치가 끝나면 {@link #clear()} 로 비운다(같은 인스턴스는 바로, 다른 인스턴스는 TTL 뒤).
 * <ul>
 *   <li>무효화 경합(QA P3-5a): 읽기 시작 때의 세대를 기억했다가, 읽는 동안 clear 가 있었으면 그 결과를 넣지 않는다 — 배치 커밋 직전에
 *       시작한 조회가 옛 스냅숏을 TTL 동안 남기지 않게.</li>
 *   <li>키 누적(QA P3-5b): 넣을 때 만료 항목을 치우고, 그래도 상한(maxEntries)을 넘으면 전부 비운다(탐험가별 키가 하루 활성 사용자만큼
 *       쌓이지 않게).</li>
 * </ul>
 */
final class ExpiringCache<K, V> {

    private final Duration ttl;
    private final Clock clock;
    private final int maxEntries;
    private final ConcurrentHashMap<K, Cached<V>> entries = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();

    ExpiringCache(Duration ttl, Clock clock, int maxEntries) {
        this.ttl = ttl;
        this.clock = clock;
        this.maxEntries = maxEntries;
    }

    V get(K key, Supplier<V> loader) {
        Instant now = clock.instant();
        Cached<V> cached = entries.get(key);
        if (cached != null && now.isBefore(cached.expiresAt())) return cached.value();
        long startedAt = generation.get();
        V value = loader.get();
        if (generation.get() == startedAt) {
            if (entries.size() >= maxEntries) evict(now);
            entries.put(key, new Cached<>(value, now.plus(ttl)));
        }
        return value;
    }

    void clear() {
        generation.incrementAndGet();
        entries.clear();
    }

    int size() {
        return entries.size();
    }

    private void evict(Instant now) {
        entries.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
        if (entries.size() >= maxEntries) entries.clear();
    }

    private record Cached<V>(V value, Instant expiresAt) {}
}
