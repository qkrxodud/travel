package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.exploration.domain.policy.CheckInPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 체크인 판단에 필요한 시각·정책 묶음.
 *
 * @param memberJoinedAt 체크인하는 멤버의 지도 가입 시각(온보딩 예외 기준)
 * @param now            처리 시각(서버 시계) — visitedAt 이 된다
 * @param zone           "하루"와 "오늘"을 판단하는 시간대
 * @param alsoUsedToday  같은 하루 상한을 함께 쓰는 다른 기록의 오늘 건수(9단계 — 개인 지도 체크인은 오늘 받은 재방문 도장 수를 더해 센다)
 */
public record CheckInContext(CheckInPolicy policy, Instant memberJoinedAt, Instant now, ZoneId zone, int alsoUsedToday) {
    public CheckInContext {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(memberJoinedAt, "memberJoinedAt");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(zone, "zone");
        if (alsoUsedToday < 0) throw new IllegalArgumentException("alsoUsedToday >= 0: " + alsoUsedToday);
    }

    /** 하루 상한을 함께 쓰는 다른 기록이 없는 체크인. */
    public CheckInContext(CheckInPolicy policy, Instant memberJoinedAt, Instant now, ZoneId zone) {
        this(policy, memberJoinedAt, now, zone, 0);
    }

    /** 같은 하루 상한을 함께 쓰는 기록 수를 더한 사본. */
    public CheckInContext alsoUsing(int usedToday) {
        return new CheckInContext(policy, memberJoinedAt, now, zone, usedToday);
    }

    /**
     * 오늘 이미 쓴 건수(used — 이 지도의 체크인 + 함께 쓰는 기록)로 하나 더 할 수 있는지. 온보딩 기간이면 상한 없음.
     * 체크인과 재방문 도장이 같은 판정을 쓴다.
     */
    public boolean capReachedWith(int used) {
        return !inOnboarding() && used + alsoUsedToday >= policy.dailyCap();
    }

    public LocalDate today() {
        return now.atZone(zone).toLocalDate();
    }

    public boolean inOnboarding() {
        return now.isBefore(memberJoinedAt.plus(policy.onboardingGrace()));
    }
}
