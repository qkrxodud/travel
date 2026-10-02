package com.kobi.territory.exploration.domain.explorer;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.Objects;

/**
 * 탐험가(계정 루트). 1~3단계는 익명(handle 없음)으로 발급한다. 3단계부터 비밀 접근 토큰의 해시를 가진다(결정 2 —
 * explorerId 는 공개 식별자, 인증은 토큰). 4단계에서 계정 연결 시 claimExplorer(익명 → 계정 병합)와 handle 지정이 붙는다.
 */
public final class Explorer {

    private final ExplorerId id;
    private final String handle;
    private final AccessTokenHash tokenHash;
    private final Instant createdAt;

    private Explorer(ExplorerId id, String handle, AccessTokenHash tokenHash, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.handle = handle;
        this.tokenHash = tokenHash;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** 익명 탐험가 발급 — 토큰은 호출자가 만들어 해시만 넘긴다. */
    public static Explorer anonymous(ExplorerId id, AccessTokenHash tokenHash, Instant now) {
        return new Explorer(id, null, Objects.requireNonNull(tokenHash, "tokenHash"), now);
    }

    /** @param tokenHash 3단계 이전에 만든 탐험가는 null(토큰으로 인증할 수 없다) */
    public static Explorer restore(ExplorerId id, String handle, AccessTokenHash tokenHash, Instant createdAt) {
        return new Explorer(id, handle, tokenHash, createdAt);
    }

    public boolean anonymous() {
        return handle == null;
    }

    public ExplorerId id() { return id; }
    public String handle() { return handle; }
    public AccessTokenHash tokenHash() { return tokenHash; }
    public Instant createdAt() { return createdAt; }
}
