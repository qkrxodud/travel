package com.kobi.territory.exploration.domain.explorer;

import java.time.Instant;
import java.util.Objects;

/**
 * 탐험가에 연결된 로그인 계정(Explorer 애그리거트 내부, 탐험가와 1:1 — account 테이블). 한 신원(provider, subject)은 한 탐험가에만.
 *
 * @param linkedAt 연결(최초 로그인) 시각
 */
public record Account(AccountIdentity identity, Instant linkedAt) {
    public Account {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(linkedAt, "linkedAt");
    }

    public boolean is(AccountIdentity other) {
        return identity.provider().equals(other.provider()) && identity.subject().equals(other.subject());
    }
}
