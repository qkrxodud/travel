package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.Objects;

/** 지도 멤버. joinedAt 은 온보딩 예외(가입 후 N시간 상한 미적용)의 기준 시각이다. */
public record Member(ExplorerId explorerId, MemberRole role, Instant joinedAt) {
    public Member {
        Objects.requireNonNull(explorerId, "explorerId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }
}
