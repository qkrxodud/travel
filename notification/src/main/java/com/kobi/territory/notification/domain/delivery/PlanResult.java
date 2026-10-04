package com.kobi.territory.notification.domain.delivery;

import java.util.Objects;
import java.util.Optional;

/** 계획 판단과(계획했으면) 새 발송 기록. */
public record PlanResult(PlanDecision decision, Optional<PushDelivery> delivery) {

    public PlanResult {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(delivery, "delivery");
        if ((decision == PlanDecision.PLANNED) != delivery.isPresent()) throw new IllegalArgumentException("계획 결과 불일치");
    }

    static PlanResult skipped(PlanDecision decision) {
        return new PlanResult(decision, Optional.empty());
    }
}
