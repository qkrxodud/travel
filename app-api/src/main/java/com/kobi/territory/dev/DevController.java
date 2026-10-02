package com.kobi.territory.dev;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.CurrentExplorer;
import com.kobi.territory.exploration.application.ExplorationDevService;
import com.kobi.territory.exploration.application.ExplorationDevService.SampleVisit;
import com.kobi.territory.exploration.application.MapAccess;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 개발·E2E 전용 엔드포인트. local 프로파일에서만 빈이 생성된다(prod에서는 404).
 * 화면의 "예시 다시 채우기"·"전부 지우기" 버튼과 E2E fixture가 쓴다.
 * <ul>
 *   <li>{@code DELETE /dev/reset} — 전체 테이블 비우기(모든 탐험가·지도·방문·outbox). 204</li>
 *   <li>{@code POST /dev/seed} — 현재 탐험가(X-Explorer-Id) 개인 지도의 방문을 지우고 프로토타입 SAMPLE 45곳을 체크인(상한 우회). 200 {seeded}</li>
 *   <li>{@code DELETE /dev/visits} — 현재 탐험가 개인 지도의 방문 전부 취소. 200 {cleared}</li>
 *   <li>{@code POST /dev/explorers/age} {hours} — 현재 탐험가의 가입·지도 가입 시각을 hours 만큼 과거로(온보딩 예외 종료 시뮬레이션). 200</li>
 * </ul>
 */
@Profile("local")
@RestController
@RequestMapping("/dev")
public class DevController {

    /** FK 역순. 새 테이블이 생기면 여기에 추가한다. */
    private static final List<String> TABLES = List.of("outbox", "visit", "territory", "map_member", "expedition_map", "explorer");

    private final JdbcTemplate jdbc;
    private final ExplorationDevService exploration;
    private final MapAccess mapAccess;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DevController(JdbcTemplate jdbc, ExplorationDevService exploration, MapAccess mapAccess,
                         ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.exploration = exploration;
        this.mapAccess = mapAccess;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @DeleteMapping("/reset")
    @Transactional
    public ResponseEntity<Void> reset() {
        TABLES.forEach(t -> jdbc.update("DELETE FROM " + t));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/seed")
    public Map<String, Integer> seed(@CurrentExplorer ExplorerId explorerId) {
        LocalDate today = LocalDate.now(clock);
        List<SampleVisit> samples = loadSamples().stream()
            .map(s -> SampleVisit.relativeTo(today, RegionCode.of(s.regionCode()), s.monthOffset(), s.day(), s.memo()))
            .toList();
        return Map.of("seeded", exploration.seed(explorerId, samples));
    }

    @DeleteMapping("/visits")
    public Map<String, Integer> clearVisits(@CurrentExplorer ExplorerId explorerId) {
        return Map.of("cleared", exploration.clear(explorerId));
    }

    @PostMapping("/explorers/age")
    @Transactional
    public Map<String, Object> age(@CurrentExplorer ExplorerId explorerId, @RequestBody AgeRequest req) {
        var explorer = mapAccess.requireExplorer(explorerId);
        Duration shift = Duration.ofHours(req.hours());
        var created = explorer.createdAt().minus(shift);
        // Hibernate 와 같은 방식(hibernate.jdbc.time_zone=UTC)으로 UTC 벽시계 값을 쓴다.
        LocalDateTime utc = LocalDateTime.ofInstant(created, ZoneOffset.UTC);
        jdbc.update("UPDATE explorer SET created_at = ? WHERE id = ?", utc, explorerId.value());
        jdbc.update("UPDATE map_member SET joined_at = ? WHERE explorer_id = ?", utc, explorerId.value());
        return Map.of("explorerId", explorerId.value(), "createdAt", created.toString());
    }

    private List<SampleJson> loadSamples() {
        try (InputStream in = new ClassPathResource("dev/sample-visits.json").getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    record SampleJson(String regionCode, int monthOffset, int day, String memo) {}

    public record AgeRequest(int hours) {}
}
