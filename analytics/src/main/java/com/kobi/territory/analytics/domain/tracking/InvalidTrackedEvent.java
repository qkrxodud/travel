package com.kobi.territory.analytics.domain.tracking;

/** 이벤트 하나가 규칙에 맞지 않음 — 묶음 안에서는 그 이벤트만 버리고 이유를 돌려준다. */
public final class InvalidTrackedEvent extends RuntimeException {

    private final RejectionReason reason;

    InvalidTrackedEvent(RejectionReason reason, String detail) {
        super(reason + ": " + detail);
        this.reason = reason;
    }

    public RejectionReason reason() {
        return reason;
    }
}
