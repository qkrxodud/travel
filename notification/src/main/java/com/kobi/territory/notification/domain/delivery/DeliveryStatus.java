package com.kobi.territory.notification.domain.delivery;

/** 발송 기록의 상태. */
public enum DeliveryStatus {
    /** 보낼 시각(또는 다시 보낼 시각)을 기다린다. */
    PENDING,
    /** 보내는 중(한 발송기가 잡았다). 오래 머물면 다시 잡을 수 있다(최소 1회 — 중복 수신 가능, 운영 문서). */
    SENDING,
    /** 한 기기라도 받았다. */
    SENT,
    /** 다시 보내도 안 되거나 재시도를 다 썼다. */
    FAILED,
    /** 그날 안에(조용한 시간 전에) 보내지 못했다 — 다음 날 늦게 보내지 않는다. */
    EXPIRED,
    /** 보낼 때 보니 그 종류를 껐거나 기기가 없다. */
    CANCELLED;

    /** 그날 "받을 알림"으로 세는 상태(하루 최대 개수) — 보냈거나 보낼 예정. */
    public boolean countsTowardDailyLimit() {
        return this == PENDING || this == SENDING || this == SENT;
    }
}
