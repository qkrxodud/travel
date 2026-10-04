package com.kobi.territory.notification.domain.push;

import java.time.Duration;

/**
 * 보내기 포트 — 기기 하나에 알림 한 건(웹 푸시: 내용 암호화 + VAPID 서명 + 푸시 서비스에 POST). 예외를 던지지 않고 결과로 돌려준다
 * (연결 실패·시간 초과는 {@link SendOutcome#RETRY}).
 *
 * @param timeToLive 기기가 꺼져 있을 때 푸시 서비스가 들고 있을 시간
 */
public interface PushSender {

    DeviceSend send(PushEndpoint endpoint, DeviceKeys keys, PushMessage message, Duration timeToLive);
}
