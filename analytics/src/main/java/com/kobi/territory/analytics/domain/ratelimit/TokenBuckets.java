package com.kobi.territory.analytics.domain.ratelimit;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 열쇠(방문 ID·주소)별 토큰 버킷. 인스턴스 메모리에만 있다 — <b>단일 인스턴스 가정</b>(여러 대로 늘리면 인스턴스마다 따로 세어 상한이 그만큼
 * 커진다. 그때는 공유 저장소(Redis 등)로 옮긴다 — 운영 문서). 열쇠가 상한을 넘으면 가득 찬(한동안 안 쓴) 버킷부터 잊는다 — 잊힌 열쇠는
 * 다시 가득 찬 버킷으로 시작하므로 결과가 같다. 그래도 넘치면(모두 쓰는 중) 새 열쇠는 거절한다(메모리 보호).
 */
public final class TokenBuckets {

    private final int capacity;
    private final int perMinute;
    private final int maxKeys;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public TokenBuckets(int capacity, int perMinute, int maxKeys) {
        this.capacity = capacity;
        this.perMinute = perMinute;
        this.maxKeys = maxKeys;
    }

    /** 이 열쇠로 요청 하나를 보낼 수 있으면 true. */
    public boolean tryTake(String key, Instant now) {
        TokenBucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= maxKeys) forgetFull(now);
            if (buckets.size() >= maxKeys) return false;
            bucket = buckets.computeIfAbsent(key, newKey -> new TokenBucket(capacity, perMinute, now));
        }
        return bucket.tryTake(now);
    }

    public int trackedKeys() {
        return buckets.size();
    }

    private void forgetFull(Instant now) {
        buckets.entrySet().removeIf(entry -> entry.getValue().fullAt(now));
    }
}
