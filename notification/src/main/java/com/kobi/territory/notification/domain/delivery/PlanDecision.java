package com.kobi.territory.notification.domain.delivery;

/** 한 사람에게 이 알림을 계획할지의 판단. */
public enum PlanDecision {
    /** 발송 기록을 만들었다. */
    PLANNED,
    /** 같은 열쇠(탐험가·종류·기간)로 이미 계획했다. */
    ALREADY_PLANNED,
    /** 그 종류를 껐다. */
    KIND_OFF,
    /** 기기가 없다. */
    NO_DEVICE,
    /** 그날 받을 알림이 이미 최대 개수다. */
    DAILY_LIMIT
}
