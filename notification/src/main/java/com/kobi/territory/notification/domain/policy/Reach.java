package com.kobi.territory.notification.domain.policy;

/** 이 종류의 알림이 그 사람에게 닿을 수 있는지. */
public enum Reach {
    /** 기기가 있고 그 종류를 켜 두었다. */
    REACHABLE,
    /** 그 종류를 껐다. */
    KIND_OFF,
    /** 구독한 기기가 없다(동의하지 않았거나 모두 해지·만료). */
    NO_DEVICE
}
