package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.exploration.domain.map.CheckInPolicy;
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
 */
public record CheckInContext(CheckInPolicy policy, Instant memberJoinedAt, Instant now, ZoneId zone) {
    public CheckInContext {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(memberJoinedAt, "memberJoinedAt");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(zone, "zone");
    }

    public LocalDate today() {
        return now.atZone(zone).toLocalDate();
    }

    public boolean inOnboarding() {
        return now.isBefore(memberJoinedAt.plus(policy.onboardingGrace()));
    }
}
