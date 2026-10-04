package com.kobi.territory.notification.application;

import com.kobi.territory.notification.domain.campaign.CampaignCalendar;
import com.kobi.territory.notification.domain.delivery.DeliveryPolicy;
import com.kobi.territory.notification.domain.recipient.DevicePolicy;
import java.time.Duration;
import java.util.Objects;

/**
 * 알림 설정값(app-api 가 territory.push.* 를 바인딩해 만든다).
 *
 * @param pageSize     계획할 때 한 번에 불러오는 받을 사람 수(대량 발송 나누기)
 * @param batchSize    발송기가 한 번에 잡는 기록 수
 * @param maxPerSecond 초당 최대 보내기 수(푸시 서비스 요청 — 인스턴스 하나 기준, 단일 인스턴스 가정)
 * @param sendTimeout  푸시 서비스 요청 하나의 시간 제한
 */
public record NotificationSettings(DevicePolicy devicePolicy, DeliveryPolicy deliveryPolicy, CampaignCalendar calendar,
                                   VapidCredentials vapid, int pageSize, int batchSize, int maxPerSecond, Duration sendTimeout) {

    public NotificationSettings {
        Objects.requireNonNull(devicePolicy, "devicePolicy");
        Objects.requireNonNull(deliveryPolicy, "deliveryPolicy");
        Objects.requireNonNull(calendar, "calendar");
        Objects.requireNonNull(vapid, "vapid");
        Objects.requireNonNull(sendTimeout, "sendTimeout");
        if (pageSize < 1 || batchSize < 1 || maxPerSecond < 1) throw new IllegalArgumentException("발송 묶음 설정은 1 이상");
        if (!calendar.quietHours().equals(deliveryPolicy.quietHours())) throw new IllegalArgumentException("조용한 시간은 하나여야 한다");
    }
}
