package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.Objects;

/**
 * 탐험가(계정 루트). 1~3단계는 익명(handle 없음)으로 발급한다.
 * 4단계에서 계정 연결 시 claimExplorer(익명 → 계정 병합)와 handle 지정이 붙는다.
 */
public final class Explorer {

    private final ExplorerId id;
    private final String handle;
    private final Instant createdAt;

    private Explorer(ExplorerId id, String handle, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.handle = handle;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Explorer anonymous(ExplorerId id, Instant now) {
        return new Explorer(id, null, now);
    }

    public static Explorer restore(ExplorerId id, String handle, Instant createdAt) {
        return new Explorer(id, handle, createdAt);
    }

    public boolean anonymous() {
        return handle == null;
    }

    public ExplorerId id() { return id; }
    public String handle() { return handle; }
    public Instant createdAt() { return createdAt; }
}
