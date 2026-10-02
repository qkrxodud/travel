package com.kobi.territory.dev;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.exploration.application.ExplorationDevService;
import com.kobi.territory.exploration.application.ExplorationDevService.SampleVisit;
import com.kobi.territory.exploration.application.MapAccess;
import com.kobi.territory.exploration.domain.Explorer;
import com.kobi.territory.outbox.OutboxRedelivery;
import com.kobi.territory.progression.application.RecalculateService;
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
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 개발·E2E 전용 엔드포인트. local 프로파일에서만 빈이 생성된다(prod에서는 404).
 * 화면의 "예시 다시 채우기"·"전부 지우기" 버튼과 E2E fixture가 쓴다.
 * <ul>
 *   <li>{@code DELETE /dev/reset} — 전체 테이블 비우기(모든 탐험가·지도·방문·outbox). 204</li>
 *   <li>{@code POST /dev/seed} — 현재 탐험가(X-Explorer-Id)의 진행을 비우고 개인 지도의 방문을 지운 뒤 프로토타입 SAMPLE 45곳을
 *       체크인(상한 우회). 200 {seeded}</li>
 *   <li>{@code DELETE /dev/visits} — 현재 탐험가 개인 지도의 방문 전부 취소 + 진행 비우기. 200 {cleared}</li>
 *   <li>{@code POST /dev/explorers/age} {hours} — 현재 탐험가의 가입·지도 가입 시각을 hours 만큼 과거로(온보딩 예외 종료 시뮬레이션). 200</li>
 *   <li>{@code POST /dev/outbox/redeliver[?eventId=&subscriber=]} — FAILED 전달을 다시 보낸다(생략하면 전체). 200 {redelivered}</li>
 *   <li>{@code POST /dev/recalculate} — 진행·도감·퀘스트 재계산 배치. X-Explorer-Id 가 있으면 그 탐험가만, 없으면 전체. 200 {recalculated}</li>
 * </ul>
 * 시드는 샘플을 방문일 순으로 체크인하고 처리 시각을 샘플 날짜로 둔다(D6) — 스트릭·월간 퀘스트가 프로토타입과 비슷하게 나온다.
 * 시드·전부 지우기는 프로토타입 fillSample·clear 처럼 그 탐험가의 진행(XP·뱃지·칭호·도감·퀘스트)을 먼저 비운다 — 취소 비대칭으로
 * 남는 보너스나, 이미 이번 달에 체크인해 과거 날짜 샘플로 스트릭이 이어지지 않는 문제를 없애려는 dev 전용 동작이다.
 */
@Profile("local")
@RestController
@RequestMapping("/dev")
public class DevController {

    /** FK 역순. 새 테이블이 생기면 여기에 추가한다. */
    private static final List<String> TABLES = List.of("outbox_delivery", "outbox", "xp_ledger", "badge_earned",
        "title_earned", "explorer_region", "set_progress", "quest_progress", "explorer_progress", "visit", "territory",
        "map_member", "expedition_map", "explorer");

    private final JdbcTemplate jdbc;
    private final ExplorationDevService exploration;
    private final MapAccess mapAccess;
    private final RecalculateService recalculate;
    private final OutboxRedelivery redelivery;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DevController(JdbcTemplate jdbc, ExplorationDevService exploration, MapAccess mapAccess,
                         RecalculateService recalculate, OutboxRedelivery redelivery, ObjectMapper objectMapper,
                         Clock clock) {
        this.redelivery = redelivery;
        this.jdbc = jdbc;
        this.exploration = exploration;
        this.mapAccess = mapAccess;
        this.recalculate = recalculate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @DeleteMapping("/reset")
    @Transactional
    public ResponseEntity<Void> reset() {
        TABLES.forEach(table -> jdbc.update("DELETE FROM " + table));
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
        jdbc.update("DELETE FROM set_progress WHERE map_id IN (SELECT map_id FROM map_member WHERE explorer_id = ?)", id);
    }

    private static final List<String> PROGRESSION_TABLES = List.of("xp_ledger", "badge_earned", "title_earned",
        "explorer_region", "quest_progress", "explorer_progress");

    @PostMapping("/outbox/redeliver")
    public Map<String, Integer> redeliver(@RequestParam(value = "eventId", required = false) Long eventId,
                                          @RequestParam(value = "subscriber", required = false) String subscriber) {
        return Map.of("redelivered", redelivery.redeliverFailed(eventId, subscriber));
    }

    @PostMapping("/recalculate")
    public Map<String, Integer> recalculate(
        @RequestHeader(value = CurrentExplorer.HEADER, required = false) String explorerId) {
        if (explorerId == null || explorerId.isBlank()) return Map.of("recalculated", recalculate.recalculateAll());
        recalculate.recalculate(ExplorerId.of(explorerId.strip()));
        return Map.of("recalculated", 1);
    }

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
