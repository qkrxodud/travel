package com.kobi.territory.social.domain.feed;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 일급 컬렉션: 보여 줄 수 있는 친구 소식(공개 범위로 거른 사람들의 거두지·숨기지 않은 소식). 같은 사람의 같은 소식은 한 번만 —
 * 가장 이른 것(예: 개인 지도와 공유 지도에서 같은 지역을 칠해도 발 도장 소식 하나, 병합으로 옮겨 온 같은 레벨 소식 하나).
 */
public final class ActivityFeed {

    private static final Comparator<FeedEntry> EARLIEST_FIRST = Comparator.comparing(FeedEntry::occurredAt)
        .thenComparing(FeedEntry::refId);
    private static final Comparator<FeedEntry> LATEST_FIRST = EARLIEST_FIRST.reversed();

    private final List<FeedEntry> entries;

    private ActivityFeed(List<FeedEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static ActivityFeed of(Collection<FeedEntry> entries) {
        Map<String, FeedEntry> firstOfEach = new LinkedHashMap<>();
        entries.stream().sorted(EARLIEST_FIRST).forEach(entry -> firstOfEach.putIfAbsent(entry.sameNewsKey(), entry));
        return new ActivityFeed(List.copyOf(firstOfEach.values()));
    }

    /** 최근 순 limit 건. */
    public List<FeedEntry> latest(int limit) {
        return entries.stream().sorted(LATEST_FIRST).limit(limit).toList();
    }

    public int size() {
        return entries.size();
    }
}
