package com.kobi.territory.config;

import com.kobi.territory.notification.application.NotificationSettings;
import com.kobi.territory.notification.application.VapidCredentials;
import com.kobi.territory.notification.domain.campaign.CampaignCalendar;
import com.kobi.territory.notification.domain.delivery.DeliveryPolicy;
import com.kobi.territory.notification.domain.delivery.RetryPolicy;
import com.kobi.territory.notification.domain.policy.QuietHours;
import com.kobi.territory.notification.domain.recipient.DevicePolicy;
import com.kobi.territory.notification.domain.recipient.EndpointRules;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 알림(12단계 웹 푸시) 설정값 → 정책 값. VAPID 키는 local 이 application-local.yml 시험용 키, 운영은 환경변수 TERRITORY_VAPID_PUBLIC_KEY ·
 * TERRITORY_VAPID_PRIVATE_KEY · TERRITORY_VAPID_SUBJECT 필수(application-prod.yml — 없으면 기동 실패, 형식·한 쌍 여부는 웹 푸시 구현이 기동할 때
 * 확인). 스케줄 cron(territory.push.schedule.*)과 발송기 주기는 해당 빈이 직접 받는다.
 */
@Configuration
public class NotificationConfig {

    @Bean
    public NotificationSettings notificationSettings(
        @Value("${territory.time-zone:Asia/Seoul}") String zone,
        @Value("${territory.push.vapid.public-key}") String vapidPublicKey,
        @Value("${territory.push.vapid.private-key}") String vapidPrivateKey,
        @Value("${territory.push.vapid.subject}") String vapidSubject,
        @Value("${territory.push.allowed-hosts}") List<String> allowedHosts,
        @Value("${territory.push.allow-localhost:false}") boolean allowLocalhost,
        @Value("${territory.push.max-devices:5}") int maxDevices,
        @Value("${territory.push.quiet-hours.start:22:00}") String quietStart,
        @Value("${territory.push.quiet-hours.end:08:00}") String quietEnd,
        @Value("${territory.push.daily-limit:1}") int dailyLimit,
        @Value("${territory.push.ttl:12h}") Duration timeToLive,
        @Value("${territory.push.schedule.streak-days-before-month-end:3}") int streakDaysBeforeMonthEnd,
        @Value("${territory.push.dispatch.max-attempts:4}") int maxAttempts,
        @Value("${territory.push.dispatch.retry-initial:5m}") Duration retryInitial,
        @Value("${territory.push.dispatch.retry-max:1h}") Duration retryMax,
        @Value("${territory.push.dispatch.claim-timeout:10m}") Duration claimTimeout,
        @Value("${territory.push.dispatch.page-size:500}") int pageSize,
        @Value("${territory.push.dispatch.batch-size:100}") int batchSize,
        @Value("${territory.push.dispatch.max-per-second:20}") int maxPerSecond,
        @Value("${territory.push.dispatch.send-timeout:10s}") Duration sendTimeout) {
        QuietHours quietHours = new QuietHours(LocalTime.parse(quietStart), LocalTime.parse(quietEnd), ZoneId.of(zone));
        return new NotificationSettings(
            new DevicePolicy(maxDevices, new EndpointRules(allowedHosts, allowLocalhost)),
            new DeliveryPolicy(quietHours, dailyLimit, new RetryPolicy(maxAttempts, retryInitial, retryMax), claimTimeout, timeToLive),
            new CampaignCalendar(quietHours, streakDaysBeforeMonthEnd),
            new VapidCredentials(vapidPublicKey, vapidPrivateKey, vapidSubject),
            pageSize, batchSize, maxPerSecond, sendTimeout);
    }
}
