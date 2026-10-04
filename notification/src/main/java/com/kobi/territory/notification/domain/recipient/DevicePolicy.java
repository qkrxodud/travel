package com.kobi.territory.notification.domain.recipient;

import java.util.Objects;

/**
 * 기기 구독 규칙(설정 territory.push.*).
 *
 * @param maxDevices    탐험가 한 명이 둘 수 있는 기기(브라우저 구독) 수 — 넘으면 가장 오래 전에 등록한 기기를 뺀다
 * @param endpointRules 받을 구독 주소
 */
public record DevicePolicy(int maxDevices, EndpointRules endpointRules) {

    public DevicePolicy {
        if (maxDevices < 1 || maxDevices > 50) throw new IllegalArgumentException("max-devices 는 1~50");
        Objects.requireNonNull(endpointRules, "endpointRules");
    }
}
