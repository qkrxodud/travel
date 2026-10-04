package com.kobi.territory.notification.api.web;

import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.api.web.PushDtos.PreferencesRequest;
import com.kobi.territory.notification.api.web.PushDtos.PreferencesResponse;
import com.kobi.territory.notification.api.web.PushDtos.SubscriptionRequest;
import com.kobi.territory.notification.api.web.PushDtos.SubscriptionResponse;
import com.kobi.territory.notification.api.web.PushDtos.UnsubscribeRequest;
import com.kobi.territory.notification.api.web.PushDtos.VapidKeyResponse;
import com.kobi.territory.notification.application.NotificationSettings;
import com.kobi.territory.notification.application.PushSubscriptionService;
import com.kobi.territory.notification.domain.NotificationError;
import com.kobi.territory.notification.domain.push.DeviceKeys;
import com.kobi.territory.notification.domain.push.PushEndpoint;
import com.kobi.territory.notification.domain.recipient.NotificationPreferences;
import com.kobi.territory.notification.domain.recipient.PushRecipient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 웹 푸시(12단계) — VAPID 공개 키, 이 브라우저 구독·해지, 종류별 알림 설정. 구독은 사용자가 "알림 받을래요?"에 예라고 하고 브라우저 권한을 준
 * 뒤에만 화면이 보낸다(서버에서는 기기가 있다는 것이 동의).
 */
@RestController
@RequestMapping("/push")
public class PushController {

    private final PushSubscriptionService subscriptions;
    private final NotificationSettings settings;

    public PushController(PushSubscriptionService subscriptions, NotificationSettings settings) {
        this.subscriptions = subscriptions;
        this.settings = settings;
    }

    /** 인증 불필요 — 브라우저 구독 전에 받는다. */
    @GetMapping("/vapid-public-key")
    public VapidKeyResponse vapidPublicKey() {
        return new VapidKeyResponse(settings.vapid().publicKey());
    }

    @PostMapping("/subscriptions")
    public SubscriptionResponse subscribe(@CurrentExplorer ExplorerId explorerId, @RequestBody SubscriptionRequest request) {
        if (request.keys() == null) throw NotificationError.INVALID_PUSH_SUBSCRIPTION.exception("keys");
        return SubscriptionResponse.from(subscriptions.subscribe(explorerId, PushEndpoint.of(request.endpoint()),
            new DeviceKeys(request.keys().p256dh(), request.keys().auth())));
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@CurrentExplorer ExplorerId explorerId, @RequestBody UnsubscribeRequest request) {
        subscriptions.unsubscribe(explorerId, PushEndpoint.of(request.endpoint()));
    }

    @GetMapping("/preferences")
    public PreferencesResponse preferences(@CurrentExplorer ExplorerId explorerId) {
        return response(subscriptions.view(explorerId));
    }

    @PutMapping("/preferences")
    public PreferencesResponse changePreferences(@CurrentExplorer ExplorerId explorerId, @RequestBody PreferencesRequest request) {
        return response(subscriptions.changePreferences(explorerId,
            NotificationPreferences.of(request.mystery(), request.streak(), request.season())));
    }

    private PreferencesResponse response(PushRecipient recipient) {
        return PreferencesResponse.from(recipient, settings.deliveryPolicy().quietHours(), settings.deliveryPolicy().dailyLimit());
    }
}
