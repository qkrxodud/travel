package com.kobi.territory.notification.domain;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.domain.campaign.Campaign;
import com.kobi.territory.notification.domain.campaign.CampaignCalendar;
import com.kobi.territory.notification.domain.campaign.CampaignMessages;
import com.kobi.territory.notification.domain.delivery.DeliveryPolicy;
import com.kobi.territory.notification.domain.delivery.RetryPolicy;
import com.kobi.territory.notification.domain.policy.NotificationKind;
import com.kobi.territory.notification.domain.policy.QuietHours;
import com.kobi.territory.notification.domain.push.DeviceKeys;
import com.kobi.territory.notification.domain.push.DeviceSend;
import com.kobi.territory.notification.domain.push.PushEndpoint;
import com.kobi.territory.notification.domain.push.PushMessage;
import com.kobi.territory.notification.domain.push.SendOutcome;
import com.kobi.territory.notification.domain.recipient.DevicePolicy;
import com.kobi.territory.notification.domain.recipient.EndpointRules;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/** 알림 도메인 테스트의 준비 문장. 서울 시각으로 읽고 쓴다. */
public final class Fixtures {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    public static final ExplorerId 탐험가 = ExplorerId.of("11111111-1111-1111-1111-111111111111");
    public static final ExplorerId 다른_탐험가 = ExplorerId.of("22222222-2222-2222-2222-222222222222");
    public static final QuietHours 조용한_시간 = new QuietHours(LocalTime.of(22, 0), LocalTime.of(8, 0), SEOUL);
    public static final RetryPolicy 재시도 = new RetryPolicy(4, Duration.ofMinutes(5), Duration.ofHours(1));
    public static final DeliveryPolicy 발송_규칙 = new DeliveryPolicy(조용한_시간, 1, 재시도, Duration.ofMinutes(10), Duration.ofHours(12));
    public static final CampaignCalendar 달력 = new CampaignCalendar(조용한_시간, 3);
    public static final EndpointRules 주소_규칙 = new EndpointRules(
        List.of("fcm.googleapis.com", "updates.push.services.mozilla.com", "*.push.apple.com"), false);
    public static final DevicePolicy 기기_규칙 = new DevicePolicy(3, 주소_규칙);
    /** 2026-10-05 은 월요일. */
    public static final LocalDate 월요일 = LocalDate.of(2026, 10, 5);
    public static final String 브라우저_키 = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    public static final String 인증_비밀 = "BTBZMqHH6r4Tts7J_aSIgg";

    private Fixtures() {}

    /** 서울 시각. */
    public static Instant 서울(LocalDate day, int hour, int minute) {
        return LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(SEOUL).toInstant();
    }

    public static PushEndpoint 크롬(String id) {
        return PushEndpoint.of("https://fcm.googleapis.com/fcm/send/" + id);
    }

    public static DeviceKeys 키() {
        return new DeviceKeys(브라우저_키, 인증_비밀);
    }

    public static PushMessage 미스터리_문구() {
        return CampaignMessages.weeklyMystery(월요일);
    }

    /** 월요일 09:00 미스터리 캠페인. */
    public static Campaign 미스터리_캠페인() {
        return 달력.weeklyMystery(월요일, 서울(월요일, 9, 0), false).orElseThrow();
    }

    public static Campaign 계절_캠페인(LocalDate day) {
        return new Campaign(NotificationKind.SEASON_START, "autumn-" + day.getYear(), day, 서울(day, 8, 30), false);
    }

    public static DeviceSend 받음(PushEndpoint endpoint) {
        return new DeviceSend(endpoint, SendOutcome.DELIVERED, Duration.ZERO, "HTTP 201");
    }

    public static DeviceSend 사라짐(PushEndpoint endpoint) {
        return new DeviceSend(endpoint, SendOutcome.GONE, Duration.ZERO, "HTTP 410");
    }

    public static DeviceSend 잠시_실패(PushEndpoint endpoint, Duration retryAfter) {
        return new DeviceSend(endpoint, SendOutcome.RETRY, retryAfter, "HTTP 503");
    }

    public static DeviceSend 거절(PushEndpoint endpoint) {
        return new DeviceSend(endpoint, SendOutcome.REJECTED, Duration.ZERO, "HTTP 403");
    }
}
