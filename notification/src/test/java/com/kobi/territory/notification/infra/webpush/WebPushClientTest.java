package com.kobi.territory.notification.infra.webpush;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.notification.application.NotificationSettings;
import com.kobi.territory.notification.application.VapidCredentials;
import com.kobi.territory.notification.domain.campaign.CampaignCalendar;
import com.kobi.territory.notification.domain.campaign.CampaignMessages;
import com.kobi.territory.notification.domain.delivery.DeliveryPolicy;
import com.kobi.territory.notification.domain.delivery.RetryPolicy;
import com.kobi.territory.notification.domain.policy.QuietHours;
import com.kobi.territory.notification.domain.push.DeviceKeys;
import com.kobi.territory.notification.domain.push.DeviceSend;
import com.kobi.territory.notification.domain.push.PushEndpoint;
import com.kobi.territory.notification.domain.push.SendOutcome;
import com.kobi.territory.notification.domain.recipient.DevicePolicy;
import com.kobi.territory.notification.domain.recipient.EndpointRules;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("웹 푸시 보내기")
class WebPushClientTest {

    private HttpServer pushService;
    private final Map<String, Object> received = new ConcurrentHashMap<>();
    private KeyPair browser;
    private byte[] auth;
    private WebPushClient client;

    @BeforeEach
    void start() throws Exception {
        pushService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pushService.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            received.put("headers", exchange.getRequestHeaders());
            received.put("body", exchange.getRequestBody().readAllBytes());
            int status = path.contains("gone") ? 410 : path.contains("busy") ? 429 : path.contains("missing") ? 404
                : path.contains("broken") ? 503 : path.contains("forbidden") ? 403 : 201;
            if (status == 429) exchange.getResponseHeaders().add("Retry-After", "120");
            if (path.contains("slow")) sleep();
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        pushService.start();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(P256.PARAMS);
        browser = generator.generateKeyPair();
        auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        client = WebPushClient.withClock(settings(Duration.ofMillis(500)), new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void stop() {
        pushService.stop(0);
    }

    private static void sleep() {
        try {
            Thread.sleep(1500);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static NotificationSettings settings(Duration timeout) {
        QuietHours quiet = new QuietHours(LocalTime.of(22, 0), LocalTime.of(8, 0), ZoneId.of("Asia/Seoul"));
        return new NotificationSettings(new DevicePolicy(5, new EndpointRules(List.of(), true)),
            new DeliveryPolicy(quiet, 1, new RetryPolicy(3, Duration.ofMinutes(1), Duration.ofMinutes(10)), Duration.ofMinutes(10),
                Duration.ofHours(12)),
            new CampaignCalendar(quiet, 3), new VapidCredentials(VapidKeysTest.PUBLIC, VapidKeysTest.PRIVATE, "mailto:ops@territory.kr"),
            100, 100, 100, timeout);
    }

    private DeviceSend 보낸다(String path) {
        PushEndpoint endpoint = PushEndpoint.of("http://127.0.0.1:" + pushService.getAddress().getPort() + "/" + path);
        DeviceKeys keys = new DeviceKeys(Base64.getUrlEncoder().withoutPadding().encodeToString(P256.encode((ECPublicKey) browser.getPublic())),
            Base64.getUrlEncoder().withoutPadding().encodeToString(auth));
        return client.send(endpoint, keys, CampaignMessages.weeklyMystery(LocalDate.of(2026, 10, 5)), Duration.ofHours(12));
    }

    @Nested
    @DisplayName("알림 서비스가 받으면")
    class Delivered {

        @Test
        @DisplayName("브라우저만 풀 수 있게 암호화한 알림을 보낸다 — 풀어 보면 종류·제목·본문·열 경로가 들어 있다")
        void encryptedPayload() throws Exception {
            DeviceSend send = 보낸다("fcm/send/abc");

            assertThat(send.outcome()).isEqualTo(SendOutcome.DELIVERED);
            String payload = new String(WebPushEncryptionTest.decrypt((byte[]) received.get("body"), (ECPrivateKey) browser.getPrivate(),
                P256.encode((ECPublicKey) browser.getPublic()), auth), StandardCharsets.UTF_8);
            assertThat(payload).contains("\"kind\":\"mystery\"").contains("\"url\":\"/?from=push&push=mystery#map\"")
                .contains("\"tag\":\"mystery-2026-10-05\"");
        }

        @Test
        @DisplayName("보관 시간·긴급도·종류·암호화 방식·서버 신원을 함께 알린다")
        void headers() {
            보낸다("fcm/send/abc");

            @SuppressWarnings("unchecked")
            Map<String, List<String>> headers = (Map<String, List<String>>) received.get("headers");
            assertThat(headers.get("Ttl")).containsExactly("43200");
            assertThat(headers.get("Urgency")).containsExactly("normal");
            assertThat(headers.get("Topic")).containsExactly("mystery");
            assertThat(headers.get("Content-encoding")).containsExactly("aes128gcm");
            assertThat(headers.get("Authorization").get(0)).startsWith("vapid t=").endsWith(", k=" + VapidKeysTest.PUBLIC);
        }
    }

    @Nested
    @DisplayName("알림 서비스가 받지 못하면")
    class NotDelivered {

        @Test
        @DisplayName("구독이 없어졌다고 하면(410·404) 그 기기를 지울 수 있게 알린다")
        void gone() {
            assertThat(보낸다("gone").outcome()).isEqualTo(SendOutcome.GONE);
            assertThat(보낸다("missing").outcome()).isEqualTo(SendOutcome.GONE);
        }

        @Test
        @DisplayName("너무 잦다고 하면 기다리라는 시간과 함께 다시 보내자고 한다")
        void tooMany() {
            DeviceSend send = 보낸다("busy");

            assertThat(send.outcome()).isEqualTo(SendOutcome.RETRY);
            assertThat(send.retryAfter()).isEqualTo(Duration.ofSeconds(120));
        }

        @Test
        @DisplayName("알림 서비스 장애·응답 없음은 다시 보내자고 한다")
        void unavailable() {
            assertThat(보낸다("broken").outcome()).isEqualTo(SendOutcome.RETRY);
            assertThat(보낸다("slow").outcome()).isEqualTo(SendOutcome.RETRY);
        }

        @Test
        @DisplayName("신원 불일치 같은 거절은 다시 보내지 않는다")
        void rejected() {
            assertThat(보낸다("forbidden").outcome()).isEqualTo(SendOutcome.REJECTED);
        }

        @Test
        @DisplayName("기다리라는 시간이 날짜 형식이거나 너무 길면 무시하거나 하루로 줄인다")
        void retryAfterFormats() {
            assertThat(WebPushClient.retryAfter("Wed, 21 Oct 2026 07:28:00 GMT")).isEqualTo(Duration.ZERO);
            assertThat(WebPushClient.retryAfter("999999")).isEqualTo(Duration.ofDays(1));
        }
    }
}
