package com.kobi.territory.dev;

import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.application.CampaignRun;
import com.kobi.territory.notification.application.CampaignRunner;
import com.kobi.territory.notification.application.DeliveryDispatcher;
import com.kobi.territory.notification.application.DispatchRun;
import com.kobi.territory.notification.application.PushDeliveryLog;
import com.kobi.territory.notification.domain.NotificationError;
import com.kobi.territory.notification.domain.delivery.PushDelivery;
import com.kobi.territory.notification.domain.policy.NotificationKind;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 웹 푸시(12단계) local 전용 — DevController 와 같은 조건(local 프로파일 + territory.dev.enabled=true)일 때만 생긴다.
 * <ul>
 *   <li>{@code POST /dev/push/send {kind, force?}} — 그 종류의 알림을 지금 계획하고 바로 발송기를 돌린다. force(기본 true)면 날짜 조건(월요일·
 *       월말 3일 전·계절 시작일)과 조용한 시간을 건너뛴다 — 동의·종류 설정·하루 최대 개수·멱등은 그대로. force=false 는 스케줄과 똑같이
 *       판단한다({@code /dev/clock} 으로 시계를 밀어 그날로 가서 확인). 200 {runs, dispatch}</li>
 *   <li>{@code POST /dev/push/dispatch} — 발송기만 지금 돌린다. 200 {outcomes, notClaimed, goneDevices}</li>
 *   <li>{@code GET /dev/push/deliveries} — 지금 탐험가의 최근 발송 기록 20개(최근 것 먼저)</li>
 *   <li>{@code POST /dev/push/inbox/{box}} — 가짜 푸시 서비스(구독 주소로 {@code http://localhost:포트/dev/push/inbox/이름}). 받은 헤더·크기를
 *       적는다. 이름이 {@code gone} 으로 시작하면 410(구독 없음), {@code busy} 면 429(Retry-After 1), {@code reject} 면 403, 아니면 201.
 *       {@code GET} 으로 받은 것 보기, {@code DELETE /dev/push/inbox} 로 비우기</li>
 * </ul>
 */
@Profile("local")
@ConditionalOnProperty(prefix = "territory.dev", name = "enabled", havingValue = "true")
@RestController
@RequestMapping("/dev/push")
public class PushDevController {

    private final CampaignRunner campaigns;
    private final DeliveryDispatcher dispatcher;
    private final PushDeliveryLog deliveryLog;
    private final Clock clock;
    private final Map<String, List<Receipt>> inbox = new ConcurrentHashMap<>();

    public PushDevController(CampaignRunner campaigns, DeliveryDispatcher dispatcher, PushDeliveryLog deliveryLog, Clock clock) {
        this.campaigns = campaigns;
        this.dispatcher = dispatcher;
        this.deliveryLog = deliveryLog;
        this.clock = clock;
    }

    public record SendRequest(String kind, Boolean force) {}

    public record SendResponse(List<CampaignRun> runs, DispatchRun dispatch) {}

    @PostMapping("/send")
    public SendResponse send(@RequestBody SendRequest request) {
        NotificationKind kind = NotificationKind.ofCode(request.kind()).orElseThrow(NotificationError.UNKNOWN_NOTIFICATION_KIND::exception);
        boolean force = request.force() == null || request.force();
        List<CampaignRun> runs = switch (kind) {
            case WEEKLY_MYSTERY -> List.of(campaigns.weeklyMystery(force));
            case STREAK_GUARD -> List.of(campaigns.streakGuard(force));
            case SEASON_START -> campaigns.seasonStarts(force);
        };
        return new SendResponse(runs, dispatcher.dispatchDue());
    }

    @PostMapping("/dispatch")
    public DispatchRun dispatch() {
        return dispatcher.dispatchDue();
    }

    public record DeliveryView(long id, String kind, String period, String deliveryDay, String status, int attempts, Instant dueAt,
                               Instant nextAttemptAt, Instant sentAt, int deliveredDevices, String lastError, String title, String body,
                               String url) {
        static DeliveryView from(PushDelivery delivery) {
            return new DeliveryView(delivery.id().orElseThrow(), delivery.key().kind().code(), delivery.key().period(),
                delivery.deliveryDay().toString(), delivery.status().name(), delivery.attempts(), delivery.dueAt(), delivery.nextAttemptAt(),
                delivery.sentAt().orElse(null), delivery.deliveredDevices(), delivery.lastError().orElse(null), delivery.message().title(),
                delivery.message().body(), delivery.message().url());
        }
    }

    @GetMapping("/deliveries")
    public List<DeliveryView> deliveries(@CurrentExplorer ExplorerId explorerId) {
        return deliveryLog.recentOf(explorerId, 20).stream().map(DeliveryView::from).toList();
    }

    /** 가짜 푸시 서비스가 받은 한 건(내용은 암호문이라 크기만). */
    public record Receipt(Instant receivedAt, String ttl, String urgency, String topic, String contentEncoding, boolean vapid, int bytes,
                          int status) {}

    @PostMapping("/inbox/{box}")
    public ResponseEntity<Void> receive(@PathVariable("box") String box, HttpServletRequest request) throws IOException {
        int status = box.startsWith("gone") ? 410 : box.startsWith("busy") ? 429 : box.startsWith("reject") ? 403 : 201;
        String authorization = request.getHeader("Authorization");
        inbox.computeIfAbsent(box, name -> new CopyOnWriteArrayList<>()).add(new Receipt(clock.instant(), request.getHeader("TTL"),
            request.getHeader("Urgency"), request.getHeader("Topic"), request.getHeader("Content-Encoding"),
            authorization != null && authorization.startsWith("vapid t=") && authorization.contains(", k="),
            request.getInputStream().readAllBytes().length, status));
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (status == 429) response.header("Retry-After", "1");
        return response.build();
    }

    @GetMapping("/inbox/{box}")
    public List<Receipt> received(@PathVariable("box") String box) {
        return inbox.getOrDefault(box, List.of());
    }

    @DeleteMapping("/inbox")
    public ResponseEntity<Void> clearInbox() {
        inbox.clear();
        return ResponseEntity.noContent().build();
    }
}
