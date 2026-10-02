package com.kobi.territory.exploration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.catalog.api.RegionCatalog;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.application.CheckInCommand;
import com.kobi.territory.exploration.application.CheckInService;
import com.kobi.territory.exploration.application.EditVisitCommand;
import com.kobi.territory.exploration.application.ExplorationDevService;
import com.kobi.territory.exploration.application.ExplorerService;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.ExplorationException;
import com.kobi.territory.outbox.OutboxEventEntity;
import com.kobi.territory.outbox.OutboxEventRepository;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.IntegrationTestConfig.CapturedEvents;
import com.kobi.territory.support.MutableClock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** D2: Flyway V1 + 체크인 트랜잭션·outbox·상한·온보딩 예외(가변 시계) 통합 검증. */
@IntegrationTest
class CheckInIntegrationTest {

    private static final RegionCode JONGNO = RegionCode.of("KR-11010");
    private static final RegionCode ULLEUNG = RegionCode.of("KR-37430");

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired ExplorerService explorers;
    @Autowired CheckInService checkIns;
    @Autowired ExplorationDevService dev;
    @Autowired OutboxEventRepository outbox;
    @Autowired MutableClock clock;
    @Autowired ObjectMapper objectMapper;
    @Autowired CapturedEvents captured;
    @Autowired RegionCatalog catalog;

    private ExplorerService.RegisteredExplorer register() {
        return explorers.registerAnonymous();
    }

    private CheckInService.CheckInOutcome checkIn(ExplorerId who, RegionCode code) {
        return checkIns.checkIn(new CheckInCommand(who, null, code, LocalDate.now(clock), null, null));
    }

    private List<OutboxEventEntity> eventsOf(String mapId) {
        return outbox.findByAggregateIdOrderByIdAsc(mapId);
    }

    private static ExplorationError errorOf(Throwable t) {
        return ((ExplorationException) t).error();
    }

    @Test
    void Flyway_V1이_적용되고_엔티티_검증을_통과한다() {
        var applied = flyway.info().applied();
        assertThat(applied).extracting(i -> i.getVersion().getVersion()).contains("1");
        assertThat(applied[0].getDescription()).isEqualTo("catalog exploration");
        List<String> tables = jdbc.queryForList(
            "SELECT LOWER(TABLE_NAME) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'", String.class);
        assertThat(tables).contains("explorer", "expedition_map", "map_member", "territory", "visit", "outbox");
    }

    @Test
    void 가입하면_개인_지도와_OWNER_멤버가_생기고_MapCreated가_적재된다() {
        var r = register();
        String mapId = r.personalMap().id().value();
        assertThat(jdbc.queryForObject("SELECT kind FROM expedition_map WHERE id = ?", String.class, mapId))
            .isEqualTo("PERSONAL");
        assertThat(jdbc.queryForObject("SELECT daily_check_in_cap FROM expedition_map WHERE id = ?", Integer.class, mapId))
            .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT role FROM map_member WHERE map_id = ? AND explorer_id = ?", String.class,
            mapId, r.explorer().id().value())).isEqualTo("OWNER");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM territory WHERE map_id = ?", Integer.class, mapId))
            .as("빈 Territory 루트 행(잠금 대상)").isEqualTo(1);
        assertThat(eventsOf(mapId)).extracting(OutboxEventEntity::eventName).containsExactly("MapCreated");
    }

    @Test
    void 체크인하면_visit과_RegionVisited가_같은_트랜잭션에_적재된다() throws Exception {
        var r = register();
        ExplorerId me = r.explorer().id();
        String mapId = r.personalMap().id().value();

        var out = checkIn(me, ULLEUNG);
        assertThat(out.preview().preview().totalXp()).isEqualTo(50 + 15 + 10);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ? AND region_code = ?", Integer.class,
            mapId, "KR-37430")).isEqualTo(1);
        OutboxEventEntity row = eventsOf(mapId).stream().filter(e -> e.eventName().equals("RegionVisited"))
            .findFirst().orElseThrow();
        assertThat(row.getAggregate()).isEqualTo("Territory");
        JsonNode p = objectMapper.readTree(row.getPayload());
        assertThat(p.get("explorerId").asText()).isEqualTo(me.value());
        assertThat(p.get("mapId").asText()).isEqualTo(mapId);
        assertThat(p.get("regionCode").asText()).isEqualTo("KR-37430");
        assertThat(p.get("rarity").asText()).isEqualTo("LEGEND");
        assertThat(p.get("provinceCode").asText()).isEqualTo("KR-37");
        assertThat(p.get("isFirstInProvince").asBoolean()).isTrue();
        assertThat(p.get("nth").asInt()).isEqualTo(1);
        assertThat(p.get("isFirstClaim").asBoolean()).isTrue();
        assertThat(p.has("visitedAt")).isTrue();

        RegionVisited back = objectMapper.readValue(row.getPayload(), RegionVisited.class);
        assertThat(back.isFirstInProvince()).isTrue();
        assertThat(back.rarity()).isEqualTo(Rarity.LEGEND);
        assertThat(back.visitedAt()).isEqualTo(clock.instant());
    }

    @Test
    void 실패한_체크인은_visit도_outbox도_남기지_않는다() {
        var r = register();
        ExplorerId me = r.explorer().id();
        String mapId = r.personalMap().id().value();
        checkIn(me, JONGNO);
        int before = eventsOf(mapId).size();

        assertThatThrownBy(() -> checkIn(me, JONGNO))
            .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.DUPLICATE_VISIT));
        assertThatThrownBy(() -> checkIns.checkIn(new CheckInCommand(me, null, ULLEUNG,
            LocalDate.now(clock).plusDays(1), null, null)))
            .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.FUTURE_VISIT_DATE));

        assertThat(eventsOf(mapId)).hasSize(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ?", Integer.class, mapId)).isEqualTo(1);
    }

    @Test
    void 온보딩_72시간은_상한_미적용_이후엔_하루_5곳_다음날_초기화() {
        var r = register();
        ExplorerId me = r.explorer().id();
        List<String> codes = List.of("KR-11010", "KR-11020", "KR-11030", "KR-11040", "KR-11050", "KR-11060",
            "KR-11070", "KR-11080", "KR-11090", "KR-11100", "KR-11110", "KR-11120", "KR-11130", "KR-11140");
        // 가입 직후: 6곳 이상도 허용
        for (int i = 0; i < 6; i++) checkIn(me, RegionCode.of(codes.get(i)));

        // 72시간 + 1시간 경과 → 온보딩 종료, 이 날 5곳까지
        clock.advance(Duration.ofHours(73));
        for (int i = 6; i < 11; i++) checkIn(me, RegionCode.of(codes.get(i)));
        assertThatThrownBy(() -> checkIn(me, RegionCode.of(codes.get(11))))
            .isInstanceOf(ExplorationException.class)
            .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.DAILY_CAP_EXCEEDED));

        // 다음 날이면 다시 가능
        clock.advance(Duration.ofDays(1));
        checkIn(me, RegionCode.of(codes.get(11)));
    }

    @Test
    void 수정과_취소는_VisitEdited_VisitCancelled를_적재하고_취소는_물리_삭제한다() {
        var r = register();
        ExplorerId me = r.explorer().id();
        String mapId = r.personalMap().id().value();
        checkIn(me, JONGNO);
        checkIns.edit(new EditVisitCommand(me, null, JONGNO, LocalDate.now(clock).minusDays(2), "야간개장", null));
        assertThat(jdbc.queryForObject("SELECT memo FROM visit WHERE map_id = ?", String.class, mapId)).isEqualTo("야간개장");

        var cancel = checkIns.cancel(me, null, JONGNO);
        assertThat(cancel.wasClaim()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ?", Integer.class, mapId)).isZero();
        assertThat(eventsOf(mapId)).extracting(OutboxEventEntity::eventName)
            .containsExactly("MapCreated", "RegionVisited", "VisitEdited", "VisitCancelled");
        assertThatThrownBy(() -> checkIns.cancel(me, null, JONGNO))
            .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ExplorationError.VISIT_NOT_FOUND));
    }

    @Test
    void outbox_릴레이가_이벤트를_발행하고_published_at을_기록한다() throws Exception {
        var r = register();
        checkIn(r.explorer().id(), JONGNO);
        String mapId = r.personalMap().id().value();
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline
            && eventsOf(mapId).stream().anyMatch(e -> e.getPublishedAt() == null)) {
            Thread.sleep(100);
        }
        assertThat(eventsOf(mapId)).allSatisfy(e -> assertThat(e.getPublishedAt()).isNotNull());
        assertThat(captured.all()).anySatisfy(e -> {
            assertThat(e).isInstanceOf(RegionVisited.class);
            RegionVisited rv = (RegionVisited) e;
            assertThat(rv.mapId()).isEqualTo(mapId);
            assertThat(rv.regionCode()).isEqualTo("KR-11010");
            assertThat(rv.nth()).isEqualTo(1);
            assertThat(rv.isFirstClaim()).isTrue();
        });
    }

    @Test
    void 개발용_시드는_온보딩이_끝난_탐험가도_상한을_우회해_칠한다() {
        var r = register();
        ExplorerId me = r.explorer().id();
        clock.advance(Duration.ofHours(100));
        var samples = catalog.activeRegions().stream().filter(x -> x.provinceCode().equals("KR-31")).limit(12)
            .map(x -> new ExplorationDevService.SampleVisit(RegionCode.of(x.code()), LocalDate.now(clock).minusMonths(3), "샘플"))
            .toList();
        assertThat(dev.seed(me, samples)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE checked_in_by = ?", Integer.class, me.value()))
            .isEqualTo(12);
        // 시드는 기존 방문을 지우고 다시 채운다
        assertThat(dev.seed(me, samples.subList(0, 3))).isEqualTo(3);
        assertThat(dev.clear(me)).isEqualTo(3);
    }
}
