package com.kobi.territory.dev;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.exploration.application.ExplorationDevService;
import com.kobi.territory.exploration.application.ExplorationDevService.SampleVisit;
import com.kobi.territory.exploration.application.MapAccess;
import com.kobi.territory.exploration.domain.explorer.Explorer;
import com.kobi.territory.outbox.OutboxRedelivery;
import com.kobi.territory.outbox.OutboxRelay;
import com.kobi.territory.api.security.ExplorerAuthentication;
import com.kobi.territory.api.security.GoogleLoginConfig;
import com.kobi.territory.api.security.LoginResponse;
import com.kobi.territory.api.security.SessionLogin;
import com.kobi.territory.exploration.domain.explorer.AccountIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kobi.territory.progression.application.RecalculateService;
import com.kobi.territory.catalog.application.ItemDefinitionCache;
import com.kobi.territory.wardrobe.application.InventoryRecalculateService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 개발·E2E 전용 엔드포인트. local 프로파일 <b>이면서</b> territory.dev.enabled=true(application-local.yml 에만 있음)일 때만 빈이
 * 생성된다(prod·설정 없음이면 404 — QA P3-11: 기본 프로파일 local 에 기대지 않는 두 번째 안전장치).
 * 화면의 "예시 다시 채우기"·"전부 지우기" 버튼과 E2E fixture가 쓴다.
 * <ul>
 *   <li>{@code DELETE /dev/reset} — 전체 테이블 비우기(모든 탐험가·지도·방문·outbox). 204</li>
 *   <li>{@code POST /dev/seed} — 현재 탐험가(X-Explorer-Token)의 진행을 비우고 개인 지도의 방문을 지운 뒤 프로토타입 SAMPLE 45곳을
 *       체크인(상한 우회). 200 {seeded}</li>
 *   <li>{@code DELETE /dev/visits} — 현재 탐험가 개인 지도의 방문 전부 취소 + 진행 비우기. 200 {cleared}</li>
 *   <li>{@code POST /dev/explorers/age} {hours} — 현재 탐험가의 가입·지도 가입 시각을 hours 만큼 과거로(온보딩 예외 종료 시뮬레이션). 200</li>
 *   <li>{@code POST /dev/outbox/redeliver[?eventId=&subscriber=]} — FAILED 전달을 다시 보낸다(생략하면 전체). 200 {redelivered}</li>
 *   <li>{@code POST /dev/recalculate} — 진행·도감·퀘스트 재계산 배치. 로그인 세션 또는 X-Explorer-Token 이 있으면 그 탐험가만, 없으면 전체.
 *       미전달 이벤트가 남은 탐험가는 보류(S3-3). 200 {recalculated, failed, deferred}</li>
 *   <li>{@code POST /dev/login} {email, sub?} — 구글 로그인 대신 같은 계정 연결·병합 경로로 세션을 만든다(4단계, E2E). 200 LoginResponse</li>
 * </ul>
 * 시드는 샘플을 방문일 순으로 체크인하고 처리 시각을 샘플 날짜로 둔다(D6) — 스트릭·월간 퀘스트가 프로토타입과 비슷하게 나온다.
 * 시드·전부 지우기는 프로토타입 fillSample·clear 처럼 그 탐험가의 진행(XP·뱃지·칭호·도감·퀘스트)을 먼저 비운다 — 취소 비대칭으로
 * 남는 보너스나, 이미 이번 달에 체크인해 과거 날짜 샘플로 스트릭이 이어지지 않는 문제를 없애려는 dev 전용 동작이다.
 */
@Profile("local")
@ConditionalOnProperty(prefix = "territory.dev", name = "enabled", havingValue = "true")
@RestController
@RequestMapping("/dev")
public class DevController {

    /** FK 역순. 새 테이블이 생기면 여기에 추가한다. item_definition(참조 데이터 — 이관 + 운영 추가)은 비우지 않는다. */
    private static final List<String> TABLES = List.of("outbox_delivery", "outbox", "owned_item_basis", "owned_item", "inventory_visit",
        "inventory", "scene", "xp_ledger", "badge_earned",
        "title_earned", "explorer_region_mark", "explorer_region", "set_progress", "quest_progress", "explorer_progress", "visit_generation", "visit", "territory",
        "map_member", "expedition_map", "recalculation_request", "handle_reservation", "account", "share_card", "privacy_settings",
        "invite_reward", "explorer");

    private final JdbcTemplate jdbc;
    private final ExplorationDevService exploration;
    private final MapAccess mapAccess;
    private final RecalculateService recalculate;
    private final OutboxRedelivery redelivery;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final InventoryRecalculateService inventoryRecalculate;
    private final ItemDefinitionCache itemDefinitions;
    private final ObjectProvider<OutboxRelay> relay;
    private final PlatformTransactionManager transactionManager;
    private final ExplorerAuthentication authentication;
    private final SessionLogin sessionLogin;

    public DevController(JdbcTemplate jdbc, ExplorationDevService exploration, MapAccess mapAccess,
                         RecalculateService recalculate, OutboxRedelivery redelivery, ObjectMapper objectMapper,
                         Clock clock, InventoryRecalculateService inventoryRecalculate,
                         ItemDefinitionCache itemDefinitions, ObjectProvider<OutboxRelay> relay,
                         PlatformTransactionManager transactionManager, ExplorerAuthentication authentication,
                         SessionLogin sessionLogin) {
        this.relay = relay;
        this.transactionManager = transactionManager;
        this.authentication = authentication;
        this.sessionLogin = sessionLogin;
        this.inventoryRecalculate = inventoryRecalculate;
        this.itemDefinitions = itemDefinitions;
        this.redelivery = redelivery;
        this.jdbc = jdbc;
        this.exploration = exploration;
        this.mapAccess = mapAccess;
        this.recalculate = recalculate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 전체 비우기. 비우는 동안 outbox 릴레이를 멈춘다 — 직전 체크인의 이벤트를 구독자가 처리하는 중에 탐험가가 지워지면 구독자
     * insert 가 FK 위반을 일으켜 reset 이 409 가 되던 경합(QA R2-1). 릴레이의 진행 중 주기가 끝나길 기다린 뒤 비우고 다시 켠다.
     */
    @DeleteMapping("/reset")
    public ResponseEntity<Void> reset() {
        relay.ifAvailable(OutboxRelay::pause);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                TABLES.forEach(table -> jdbc.update("DELETE FROM " + table));
                // 운영 추가 아이템(event: 등)만 지운다 — 이관 데이터(region:·set:, 250+9 + V4_1 invite: 2)는 유지(QA P3-16)
                jdbc.update("DELETE FROM item_definition WHERE item_id NOT LIKE 'region:%' AND item_id NOT LIKE 'set:%'"
                    + " AND item_id NOT LIKE 'invite:%'");
            });
        } finally {
            relay.ifAvailable(OutboxRelay::resume);
        }
        itemDefinitions.invalidate();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/seed")
    public Map<String, Integer> seed(@CurrentExplorer ExplorerId explorerId) {
        LocalDate today = LocalDate.now(clock);
        // 방문일 순(같으면 원래 순서) — 처리 시각 순으로 쌓여야 스트릭이 프로토타입처럼 이어진다(D6)
        List<SampleVisit> samples = loadSamples().stream()
            .map(sample -> SampleVisit.relativeTo(today, RegionCode.of(sample.regionCode()), sample.monthOffset(), sample.day(),
                sample.memo()))
            .sorted(Comparator.comparing(SampleVisit::visitDate))
            .toList();
        resetProgression(explorerId);
        return Map.of("seeded", exploration.seed(explorerId, samples));
    }

    @DeleteMapping("/visits")
    public Map<String, Integer> clearVisits(@CurrentExplorer ExplorerId explorerId) {
        int cleared = exploration.clear(explorerId);
        resetProgression(explorerId);
        return Map.of("cleared", cleared);
    }

    /** 이 탐험가의 진행 데이터 삭제(dev 전용). 이후 도착하는 VisitCancelled 는 빈 진행에 no-op 이다. */
    private void resetProgression(ExplorerId explorerId) {
        String id = mapAccess.requireExplorer(explorerId).id().value();
        PROGRESSION_TABLES.forEach(table -> jdbc.update("DELETE FROM " + table + " WHERE explorer_id = ?", id));
        // 진행 루트 행은 지우지 않고 시작 상태로 되돌린다 — 루트 선생성(S3-1) 전제 유지(QA Q-R2-2). version 을 올려 동시 갱신을 드러낸다.
        jdbc.update("UPDATE explorer_progress SET xp = 0, level = 1, title_id = NULL, streak_months = 0, streak_last_month = NULL, "
            + "version = version + 1 WHERE explorer_id = ?", id);
        // 시작 상태의 레벨 1 칭호(ExplorerProgress.start 와 같게)
        jdbc.update("INSERT INTO title_earned (explorer_id, title_id, earned_at) SELECT explorer_id, 'lv1', updated_at "
            + "FROM explorer_progress WHERE explorer_id = ?", id);
        // 개인 지도 도감만 — 공유 지도 도감은 다른 멤버 것이기도 하다(QA P3-13)
        jdbc.update("DELETE FROM set_progress WHERE map_id IN (SELECT id FROM expedition_map WHERE owner_id = ? AND kind = 'PERSONAL')", id);
        WARDROBE_CHILD_TABLES.forEach(table -> jdbc.update("DELETE FROM " + table + " WHERE explorer_id = ?", id));
        jdbc.update("UPDATE scene SET slot_hat = NULL, slot_hand = NULL, slot_badge = NULL, slot_bag = NULL, slot_pet = NULL, "
            + "slot_bg = NULL, props = '', version = version + 1 WHERE explorer_id = ?", id);
    }

    /**
     * 꾸미기(3단계) — 시드·전부 지우기 때 가방·장면도 비운다(세트 배경처럼 회수 없는 보상이 남지 않게). 루트 행(inventory·scene)은
     * 지우지 않고 자식 행·착용 칸만 비운다 — 이후 이벤트 처리·재계산이 항상 있는 루트 행을 잠근다는 전제(S3-1)를 지킨다(Q-R2-2).
     */
    private static final List<String> WARDROBE_CHILD_TABLES = List.of("owned_item_basis", "owned_item", "inventory_visit");

    private static final List<String> PROGRESSION_TABLES = List.of("xp_ledger", "badge_earned", "title_earned",
        "explorer_region_mark", "explorer_region", "quest_progress");

    @PostMapping("/outbox/redeliver")
    public Map<String, Integer> redeliver(@RequestParam(value = "eventId", required = false) Long eventId,
                                          @RequestParam(value = "subscriber", required = false) String subscriber) {
        return Map.of("redelivered", redelivery.redeliverFailed(eventId, subscriber));
    }

    @PostMapping("/recalculate")
    public Map<String, Object> recalculate(HttpServletRequest request) {
        Optional<ExplorerId> target = authentication.resolve(request); // 세션 → 토큰(4단계), 둘 다 없으면 전체
        if (target.isEmpty()) {
            RecalculateService.RecalculationReport report = recalculate.recalculateAll();
            InventoryRecalculateService.RecalculationReport wardrobe = inventoryRecalculate.recalculateAll();
            return Map.of("recalculated", report.recalculated(), "failed", report.failedExplorerIds(),
                "deferred", report.deferredExplorerIds(), "wardrobe", Map.of("recalculated", wardrobe.recalculated(),
                    "failed", wardrobe.failedExplorerIds(), "deferred", wardrobe.deferredExplorerIds()));
        }
        String explorerId = target.get().value();
        boolean done = recalculate.recalculateIfSettled(target.get());
        boolean wardrobeDone = inventoryRecalculate.recalculateIfSettled(target.get());
        return Map.of("recalculated", done ? 1 : 0, "failed", List.of(), "deferred", done ? List.of() : List.of(explorerId),
            "wardrobe", Map.of("recalculated", wardrobeDone ? 1 : 0, "failed", List.of(),
                "deferred", wardrobeDone ? List.of() : List.of(explorerId)));
    }

    /**
     * 구글 로그인 대신(local·E2E): 실제 OIDC 성공과 같은 계정 연결·병합 경로(SessionLogin → AccountService.login)를 타고 세션을 만든다.
     * X-Explorer-Token 이 있으면 그 익명 탐험가가 연결·병합 대상. sub 생략 = "dev:{email}". 200 LoginResponse + Set-Cookie JSESSIONID.
     */
    @PostMapping("/login")
    public LoginResponse login(@RequestBody DevLoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        String email = body == null ? null : body.email();
        String subject = body == null || body.sub() == null || body.sub().isBlank() ? "dev:" + email : body.sub();
        return sessionLogin.login(request, response,
            new AccountIdentity(GoogleLoginConfig.REGISTRATION_ID, subject, String.valueOf(email)),
            authentication.anonymous(request).orElse(null));
    }

    public record DevLoginRequest(String email, String sub) {}

    @PostMapping("/explorers/age")
    @Transactional
    public Map<String, Object> age(@CurrentExplorer ExplorerId explorerId, @RequestBody AgeRequest req) {
        Explorer explorer = mapAccess.requireExplorer(explorerId);
        Duration shift = Duration.ofHours(req.hours());
        Instant created = explorer.createdAt().minus(shift);
        // Hibernate 와 같은 방식(hibernate.jdbc.time_zone=UTC)으로 UTC 벽시계 값을 쓴다.
        LocalDateTime utc = LocalDateTime.ofInstant(created, ZoneOffset.UTC);
        jdbc.update("UPDATE explorer SET created_at = ? WHERE id = ?", utc, explorerId.value());
        jdbc.update("UPDATE map_member SET joined_at = ? WHERE explorer_id = ?", utc, explorerId.value());
        return Map.of("explorerId", explorerId.value(), "createdAt", created.toString());
    }

    private List<SampleJson> loadSamples() {
        try (InputStream in = new ClassPathResource("dev/sample-visits.json").getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {});
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    record SampleJson(String regionCode, int monthOffset, int day, String memo) {}

    public record AgeRequest(int hours) {}
}
