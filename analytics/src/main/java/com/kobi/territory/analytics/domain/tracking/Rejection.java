package com.kobi.territory.analytics.domain.tracking;

/** 묶음 안에서 받지 않은 이벤트 하나(몇 번째, 이름, 이유). */
public record Rejection(int index, String name, RejectionReason reason) {}
