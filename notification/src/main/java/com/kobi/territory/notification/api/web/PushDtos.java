package com.kobi.territory.notification.api.web;

import com.kobi.territory.notification.domain.policy.QuietHours;
import com.kobi.territory.notification.domain.recipient.NotificationPreferences;
import com.kobi.territory.notification.domain.recipient.PushRecipient;
import com.kobi.territory.notification.domain.recipient.SubscribeResult;

/** 알림 API 의 요청·응답(계약 _workspace/12_contracts.md). */
public final class PushDtos {

    private PushDtos() {}

    /** GET /push/vapid-public-key — 브라우저 구독의 applicationServerKey(base64url). */
    public record VapidKeyResponse(String publicKey) {}

    /** POST /push/subscriptions — 브라우저 PushSubscription.toJSON() 모양 그대로({endpoint, expirationTime?, keys: {p256dh, auth}}). */
    public record SubscriptionRequest(String endpoint, Long expirationTime, Keys keys) {
        public record Keys(String p256dh, String auth) {}
    }

    /** DELETE /push/subscriptions — 해지할 브라우저 구독 주소. */
    public record UnsubscribeRequest(String endpoint) {}

    /**
     * @param created     새 기기면 true(이미 있던 기기의 갱신이면 false)
     * @param devices     지금 알림 받는 기기 수
     * @param evicted     기기 수 상한 때문에 뺀 오래된 기기 수
     */
    public record SubscriptionResponse(boolean created, int devices, int evicted) {
        static SubscriptionResponse from(SubscribeResult result) {
            return new SubscriptionResponse(result.added(), result.deviceCount(), result.evicted().size());
        }
    }

    /** PUT /push/preferences — 세 값 모두 필수. */
    public record PreferencesRequest(Boolean mystery, Boolean streak, Boolean season) {}

    /**
     * GET·PUT /push/preferences.
     *
     * @param devices    알림 받는 기기 수(0 이면 이 탐험가에게는 아무 알림도 가지 않는다)
     * @param quietHours 조용한 시간(이 시간에는 보내지 않고 다음 허용 시각으로 미룬다)
     * @param dailyLimit 하루에 받는 알림 최대 개수
     */
    public record PreferencesResponse(boolean mystery, boolean streak, boolean season, int devices, QuietHoursView quietHours,
                                      int dailyLimit) {
        static PreferencesResponse from(PushRecipient recipient, QuietHours quiet, int dailyLimit) {
            NotificationPreferences preferences = recipient.preferences();
            return new PreferencesResponse(preferences.mystery(), preferences.streak(), preferences.season(), recipient.devices().count(),
                new QuietHoursView(quiet.start().toString(), quiet.end().toString(), quiet.zone().getId()), dailyLimit);
        }
    }

    /** @param start·end {@code HH:mm}(서비스 시간대) */
    public record QuietHoursView(String start, String end, String timeZone) {}
}
