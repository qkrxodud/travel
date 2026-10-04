package com.kobi.territory.analytics.infra.entity;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** 보관 기간 삭제를 조각으로 나눈다 — 한 번에 SIZE 줄씩 골라 지우고, 더 없을 때까지(운영 MySQL 을 오래 잡지 않게). */
public final class PurgeChunk {

    public static final int SIZE = 5_000;

    private PurgeChunk() {}

    /** @param nextKeys 다음 조각의 열쇠들(최대 SIZE) · @param delete 그 열쇠들을 지우고 지운 수 · @return 지운 합 */
    public static <K> int drain(Supplier<List<K>> nextKeys, Function<List<K>, Integer> delete) {
        int total = 0;
        List<K> keys;
        do {
            keys = nextKeys.get();
            if (!keys.isEmpty()) total += delete.apply(keys);
        } while (keys.size() == SIZE);
        return total;
    }
}
