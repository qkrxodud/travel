package com.kobi.territory.notification.application;

import com.kobi.territory.notification.domain.delivery.PlanDecision;
import com.kobi.territory.notification.domain.policy.NotificationKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 알림 계획 한 번의 결과(스케줄·local 즉시 발송 응답·로그).
 *
 * @param period    기간(오늘이 그 알림의 날이 아니면 null — 아무것도 계획하지 않음)
 * @param decisions 판단별 사람 수
 * @param notTarget 대상이 아니라 건너뛴 사람 수(스트릭 지키기: 이번 달에 이미 칠했거나 이어지는 연속이 없음)
 */
public record CampaignRun(NotificationKind kind, String period, LocalDate deliveryDay, Instant dueAt, Map<PlanDecision, Integer> decisions,
                          int notTarget) {

    public CampaignRun {
        EnumMap<PlanDecision, Integer> copy = new EnumMap<>(PlanDecision.class);
        copy.putAll(decisions);
        decisions = Collections.unmodifiableMap(copy);
    }

    static CampaignRun notToday(NotificationKind kind) {
        return new CampaignRun(kind, null, null, null, Map.of(), 0);
    }

    public int planned() {
        return decisions.getOrDefault(PlanDecision.PLANNED, 0);
    }
}
