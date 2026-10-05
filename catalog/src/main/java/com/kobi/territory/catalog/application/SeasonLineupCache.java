package com.kobi.territory.catalog.application;

import com.kobi.territory.catalog.api.query.SeasonLineupQuery.SeasonLineupView;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 회차 지역 목록 짧은 캐시 — 진행(체크인 소식마다 계절 달력이 회차 지역을 묻는다)이 매번 season_lineup 을 읽지 않게. 갱신·확정은 커밋 뒤에
 * {@link #invalidate} 로 세대를 올린다(아이템 정의 캐시와 같은 방식). 다른 인스턴스에서 확정한 목록은 최대 {@link #TTL} 뒤에 보인다 — 확정은
 * 회차 시작 전에만 되므로 열린 회차의 목록은 어느 인스턴스에서나 같다.
 */
@Component
public class SeasonLineupCache {

    static final Duration TTL = Duration.ofSeconds(30);

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();

    public Optional<SeasonLineupView> get(String roundId, Supplier<Optional<SeasonLineupView>> loader) {
        long now = System.nanoTime();
        long seen = generation.get();
        Entry cached = entries.get(roundId);
        if (cached != null && cached.generation() == seen && now - cached.loadedAtNanos() <= TTL.toNanos()) return cached.view();
        Optional<SeasonLineupView> loaded = loader.get();
        if (generation.get() == seen) entries.put(roundId, new Entry(loaded, now, seen));
        return loaded;
    }

    /** 목록이 바뀌었다(커밋 뒤에 부른다). */
    public void invalidate() {
        generation.incrementAndGet();
        entries.clear();
    }

    private record Entry(Optional<SeasonLineupView> view, long loadedAtNanos, long generation) {}
}
