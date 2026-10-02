package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 탈퇴 유예 중인 멤버(map_member.left_at). 유예 안에 다시 합류하면 원래 가입 시각으로 복귀하고, 유예가 끝나면 지워진다.
 *
 * @param joinedAt 원래 가입 시각(재가입 시 그대로 — 온보딩 예외를 다시 받지 않게)
 */
public record Departure(ExplorerId explorerId, Instant joinedAt, Instant leftAt) {
    public Departure {
        Objects.requireNonNull(explorerId, "explorerId");
        Objects.requireNonNull(joinedAt, "joinedAt");
        Objects.requireNonNull(leftAt, "leftAt");
    }

    /** 하드 삭제 시각. */
    public Instant purgeAfter(Duration grace) {
        return leftAt.plus(grace);
    }

    /** 유예가 끝났는지(now 기준). */
    public boolean expired(Instant now, Duration grace) {
        return !now.isBefore(purgeAfter(grace));
    }
}
