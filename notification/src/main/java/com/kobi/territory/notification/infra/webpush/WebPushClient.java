package com.kobi.territory.notification.infra.webpush;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.notification.application.NotificationSettings;
import com.kobi.territory.notification.application.VapidCredentials;
import com.kobi.territory.notification.domain.push.DeviceKeys;
import com.kobi.territory.notification.domain.push.DeviceSend;
import com.kobi.territory.notification.domain.push.PushEndpoint;
import com.kobi.territory.notification.domain.push.PushMessage;
import com.kobi.territory.notification.domain.push.PushSender;
import com.kobi.territory.notification.domain.push.SendOutcome;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 보내기 포트의 웹 푸시 구현 — 내용을 받는 브라우저 키로 암호화(RFC 8291)하고 VAPID(RFC 8292) 서명을 붙여 구독 주소(푸시 서비스)에 POST 한다.
 * JDK HttpClient(리디렉션을 따라가지 않음 — 구독 주소 밖으로 요청이 새지 않게), 요청 하나의 시간 제한은 설정값.
 * <ul>
 *   <li>헤더: {@code TTL}(기기가 꺼져 있을 때 들고 있을 초), {@code Urgency: normal}, {@code Topic}(같은 종류는 기기에 하나만 대기),
 *       {@code Content-Encoding: aes128gcm}, {@code Authorization: vapid t=…, k=…}</li>
 *   <li>응답: 2xx 받음, 404·410 구독 없음(기기 지움), 429·5xx·연결 실패 잠시 뒤 다시(Retry-After 존중), 그 밖 4xx 거절</li>
 *   <li>VAPID JWT 는 푸시 서비스(출처)마다 만들어 수명의 절반 동안 다시 쓴다(서명 비용)</li>
 * </ul>
 * 본문 JSON: {@code {kind, title, body, url, tag}} — 화면의 서비스워커가 알림으로 띄우고 누르면 url 을 연다.
 */
@Component
class WebPushClient implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushClient.class);
    private static final Duration JWT_LIFETIME = Duration.ofHours(12);

    private final VapidKeys vapid;
    private final WebPushEncryption encryption = new WebPushEncryption(new SecureRandom());
    private final HttpClient http;
    private final Duration timeout;
    private final ObjectMapper json;
    private final Clock clock;
    private final Map<String, SignedToken> tokens = new ConcurrentHashMap<>();

    @Autowired
    WebPushClient(NotificationSettings settings, ObjectMapper json) {
        this(settings, json, Clock.systemUTC());
    }

    /**
     * @param clock VAPID 만료 시각용 — 실제 시각이어야 한다(푸시 서비스가 지금부터 24시간 안만 받는다). 서버 시계(local 은 앞으로 밀 수 있음)를 쓰지
     *              않는다.
     */
    private WebPushClient(NotificationSettings settings, ObjectMapper json, Clock clock) {
        VapidCredentials credentials = settings.vapid();
        this.vapid = VapidKeys.of(credentials.publicKey(), credentials.privateKey(), credentials.subject());
        this.timeout = settings.sendTimeout();
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
        this.json = json;
        this.clock = clock;
        log.info("웹 푸시 준비: VAPID 공개 키 {}…", vapid.publicKey().substring(0, 12));
    }

    /** 시험용 — VAPID 만료 시각의 시계를 정한다. */
    static WebPushClient withClock(NotificationSettings settings, ObjectMapper json, Clock clock) {
        return new WebPushClient(settings, json, clock);
    }

    @Override
    public DeviceSend send(PushEndpoint endpoint, DeviceKeys keys, PushMessage message, Duration timeToLive) {
        byte[] body;
        try {
            body = encryption.encrypt(payload(message), keys.publicKeyBytes(), keys.authBytes());
        } catch (IllegalArgumentException unusableKeys) {
            return new DeviceSend(endpoint, SendOutcome.REJECTED, Duration.ZERO, "브라우저 키를 쓸 수 없음");
        }
        URI uri = endpoint.uri();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
            .header("TTL", Long.toString(timeToLive.toSeconds()))
            .header("Urgency", "normal")
            .header("Topic", message.kind().code())
            .header("Content-Type", "application/octet-stream")
            .header("Content-Encoding", "aes128gcm")
            .header("Authorization", authorization(uri))
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        try {
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            return outcome(endpoint, response.statusCode(), response.headers().firstValue("Retry-After").orElse(null));
        } catch (IOException unreachable) {
            return new DeviceSend(endpoint, SendOutcome.RETRY, Duration.ZERO, "연결 실패: " + unreachable.getClass().getSimpleName());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new DeviceSend(endpoint, SendOutcome.RETRY, Duration.ZERO, "중단됨");
        }
    }

    /** 브라우저 구독(applicationServerKey)에 넘기는 VAPID 공개 키. */
    String publicKey() {
        return vapid.publicKey();
    }

    static DeviceSend outcome(PushEndpoint endpoint, int status, String retryAfter) {
        String detail = "HTTP " + status;
        if (status >= 200 && status < 300) return new DeviceSend(endpoint, SendOutcome.DELIVERED, Duration.ZERO, detail);
        if (status == 404 || status == 410) return new DeviceSend(endpoint, SendOutcome.GONE, Duration.ZERO, detail);
        if (status == 429 || status >= 500) return new DeviceSend(endpoint, SendOutcome.RETRY, retryAfter(retryAfter), detail);
        return new DeviceSend(endpoint, SendOutcome.REJECTED, Duration.ZERO, detail);
    }

    /** Retry-After(초 수만 — 날짜 형식이면 무시). */
    static Duration retryAfter(String header) {
        if (header == null) return Duration.ZERO;
        try {
            return Duration.ofSeconds(Math.min(Long.parseLong(header.trim()), Duration.ofDays(1).toSeconds()));
        } catch (NumberFormatException httpDate) {
            return Duration.ZERO;
        }
    }

    private byte[] payload(PushMessage message) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("kind", message.kind().code());
        fields.put("title", message.title());
        fields.put("body", message.body());
        fields.put("url", message.url());
        fields.put("tag", message.tag());
        try {
            return json.writeValueAsString(fields).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String authorization(URI endpoint) {
        String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        Instant now = clock.instant();
        SignedToken token = tokens.compute(audience, (key, existing) -> existing != null && existing.reusableAt(now) ? existing
            : new SignedToken(vapid.authorization(endpoint, now, JWT_LIFETIME), now.plus(JWT_LIFETIME.dividedBy(2))));
        return token.header();
    }

    private record SignedToken(String header, Instant reuseUntil) {
        boolean reusableAt(Instant now) {
            return now.isBefore(reuseUntil);
        }
    }
}
