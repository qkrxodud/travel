package com.kobi.territory.notification.domain.delivery;

/** 발송기가 기록을 잡으려 할 때의 결과. */
public enum ClaimOutcome {
    /** 잡았다 — 지금 보낸다. */
    CLAIMED,
    /** 아직 보낼 때가 아니거나 다른 발송기가 보내는 중·이미 끝났다. */
    NOT_DUE,
    /** 그날 안에 보낼 수 없게 됐다(조용한 시간·날짜가 바뀜) — 만료로 닫았다. */
    EXPIRED
}
