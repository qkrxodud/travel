package com.kobi.territory.exploration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.catalog.api.query.RegionCatalog;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 1단계 D2: Flyway V1 + 체크인 트랜잭션·outbox·상한·온보딩 예외(가변 시계) 통합 검증. */
@IntegrationTest
@DisplayName("체크인의 저장과 소식")
class CheckInIntegrationTest {

    private static final RegionCode JONGNO = RegionCode.of("KR-11010");
    private static final RegionCode ULLEUNG = RegionCode.of("KR-37430");
    /** 서울 14곳 */
    private static final List<String> SEOUL = List.of("KR-11010", "KR-11020", "KR-11030", "KR-11040", "KR-11050", "KR-11060",
        "KR-11070", "KR-11080", "KR-11090", "KR-11100", "KR-11110", "KR-11120", "KR-11130", "KR-11140");

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

    private ExplorerService.RegisteredExplorer 가입() {
        return explorers.registerAnonymous();
    }

    private ExplorerId 탐험가() {
        return 가입().explorer().id();
    }

    private CheckInService.CheckInOutcome 칠한다(ExplorerId who, RegionCode code) {
        return checkIns.checkIn(new CheckInCommand(who, null, code, LocalDate.now(clock), null, null));
    }

    private void 서울을_칠한다(ExplorerId who, int from, int to) {
        for (int i = from; i < to; i++) 칠한다(who, RegionCode.of(SEOUL.get(i)));
    }

    private List<OutboxEventEntity> 소식(String mapId) {
        return outbox.findByAggregateIdOrderByIdAsc(mapId);
    }

    private int 방문_수(String mapId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ?", Integer.class, mapId);
    }

    private static ExplorationError errorOf(Throwable thrown) {
        return ((ExplorationException) thrown).error();
    }

    @Test
    @DisplayName("서비스가 뜨면 탐험 저장 구조가 첫 판 그대로 갖춰지고 저장 모델과 맞는다")
    void schemaIsReady() {
        var applied = flyway.info().applied();
        assertThat(applied).extracting(i -> i.getVersion().getVersion()).contains("1");
        assertThat(applied[0].getDescription()).isEqualTo("catalog exploration");
        List<String> tables = jdbc.queryForList(
            "SELECT LOWER(TABLE_NAME) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'", String.class);
        assertThat(tables).contains("explorer", "expedition_map", "map_member", "territory", "visit", "outbox");
    }

    @Nested
    @DisplayName("가입하면")
    class Registration {

        @Test
        @DisplayName("하루 상한 기본값을 가진 개인 지도가 생기고 나는 그 지도장이다")
        void personalMapWithOwner() {
            ExplorerService.RegisteredExplorer registered = 가입();
            String mapId = registered.personalMap().id().value();
            assertThat(jdbc.queryForObject("SELECT kind FROM expedition_map WHERE id = ?", String.class, mapId))
                .isEqualTo("PERSONAL");
            assertThat(jdbc.queryForObject("SELECT daily_check_in_cap FROM expedition_map WHERE id = ?", Integer.class, mapId))
                .isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT role FROM map_member WHERE map_id = ? AND explorer_id = ?", String.class,
                mapId, registered.explorer().id().value())).isEqualTo("OWNER");
        }

        @Test
        @DisplayName("빈 영토와 지도가 생겼다는 소식이 함께 남는다")
        void emptyTerritoryAndMapCreatedNews() {
            String mapId = 가입().personalMap().id().value();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM territory WHERE map_id = ?", Integer.class, mapId))
                .as("빈 Territory 루트 행(잠금 대상)").isEqualTo(1);
            assertThat(소식(mapId)).extracting(OutboxEventEntity::eventName).containsExactly("MapCreated");
        }
    }

    @Nested
    @DisplayName("지역을 칠하면")
    class CheckIn {

        @Test
        @DisplayName("방문과 칠했다는 소식이 함께 저장되고, 소식에는 시·도 첫 발·회차·선점처럼 하류가 다시 묻지 않아도 될 값이 실린다")
        void visitAndNewsSavedTogether() throws Exception {
            ExplorerService.RegisteredExplorer registered = 가입();
            ExplorerId me = registered.explorer().id();
            String mapId = registered.personalMap().id().value();

            var out = 칠한다(me, ULLEUNG);
            assertThat(out.preview().preview().totalXp()).isEqualTo(50 + 15 + 10);

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ? AND region_code = ?", Integer.class,
                mapId, "KR-37430")).isEqualTo(1);
            OutboxEventEntity row = 소식(mapId).stream().filter(entity -> entity.eventName().equals("RegionVisited"))
                .findFirst().orElseThrow();
            assertThat(row.aggregate()).isEqualTo("Territory");
            JsonNode payload = objectMapper.readTree(row.payload());
            assertThat(payload.get("explorerId").asText()).isEqualTo(me.value());
            assertThat(payload.get("mapId").asText()).isEqualTo(mapId);
            assertThat(payload.get("regionCode").asText()).isEqualTo("KR-37430");
            assertThat(payload.get("rarity").asText()).isEqualTo("LEGEND");
            assertThat(payload.get("provinceCode").asText()).isEqualTo("KR-37");
            assertThat(payload.get("isFirstInProvince").asBoolean()).isTrue();
            assertThat(payload.get("nth").asInt()).isEqualTo(1);
            assertThat(payload.get("isFirstClaim").asBoolean()).isTrue();
            assertThat(payload.has("visitedAt")).isTrue();

            RegionVisited back = objectMapper.readValue(row.payload(), RegionVisited.class);
            assertThat(back.isFirstInProvince()).isTrue();
            assertThat(back.rarity()).isEqualTo(Rarity.LEGEND);
            assertThat(back.visitedAt()).isEqualTo(clock.instant());
        }

        @Test
        @DisplayName("소식은 구독자에게 실려 나가고 나간 시각이 남는다")
        void newsIsRelayed() throws Exception {
            ExplorerService.RegisteredExplorer registered = 가입();
            칠한다(registered.explorer().id(), JONGNO);
            String mapId = registered.personalMap().id().value();
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline
                && 소식(mapId).stream().anyMatch(entity -> entity.publishedAt() == null)) {
                Thread.sleep(100);
            }
            assertThat(소식(mapId)).allSatisfy(entity -> assertThat(entity.publishedAt()).isNotNull());
            assertThat(captured.all()).anySatisfy(event -> {
                assertThat(event).isInstanceOf(RegionVisited.class);
                RegionVisited visited = (RegionVisited) event;
                assertThat(visited.mapId()).isEqualTo(mapId);
                assertThat(visited.regionCode()).isEqualTo("KR-11010");
                assertThat(visited.nth()).isEqualTo(1);
                assertThat(visited.isFirstClaim()).isTrue();
            });
        }

        @Test
        @DisplayName("거절된 체크인은 방문도 소식도 남기지 않는다")
        void rejectedCheckInLeavesNothing() {
            ExplorerService.RegisteredExplorer registered = 가입();
            ExplorerId me = registered.explorer().id();
            String mapId = registered.personalMap().id().value();
            칠한다(me, JONGNO);
            int before = 소식(mapId).size();

            assertThatThrownBy(() -> 칠한다(me, JONGNO))
                .satisfies(thrown -> assertThat(errorOf(thrown)).isEqualTo(ExplorationError.DUPLICATE_VISIT));
            assertThatThrownBy(() -> checkIns.checkIn(new CheckInCommand(me, null, ULLEUNG,
                LocalDate.now(clock).plusDays(1), null, null)))
                .satisfies(thrown -> assertThat(errorOf(thrown)).isEqualTo(ExplorationError.FUTURE_VISIT_DATE));

            assertThat(소식(mapId)).hasSize(before);
            assertThat(방문_수(mapId)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("하루 상한")
    class DailyCap {

        @Test
        @DisplayName("가입 후 72시간 동안은 하루 다섯 곳을 넘겨 칠할 수 있다")
        void onboardingHasNoCap() {
            ExplorerId me = 탐험가();
            서울을_칠한다(me, 0, 6);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE checked_in_by = ?", Integer.class, me.value()))
                .isEqualTo(6);
        }

        @Test
        @DisplayName("72시간이 지나면 하루 다섯 곳까지만 칠해진다")
        void capAfterOnboarding() {
            ExplorerId me = 탐험가();
            서울을_칠한다(me, 0, 6);
            clock.advance(Duration.ofHours(73));
            서울을_칠한다(me, 6, 11);
            assertThatThrownBy(() -> 칠한다(me, RegionCode.of(SEOUL.get(11))))
                .isInstanceOf(ExplorationException.class)
                .satisfies(thrown -> assertThat(errorOf(thrown)).isEqualTo(ExplorationError.DAILY_CAP_EXCEEDED));
        }

        @Test
        @DisplayName("다음 날이 되면 다시 칠할 수 있다")
        void resetsNextDay() {
            ExplorerId me = 탐험가();
            clock.advance(Duration.ofHours(73));
            서울을_칠한다(me, 0, 5);
            clock.advance(Duration.ofDays(1));
            칠한다(me, RegionCode.of(SEOUL.get(5)));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE checked_in_by = ?", Integer.class, me.value()))
                .isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("칠한 곳을 고치거나 취소하면")
    class EditAndCancel {

        @Test
        @DisplayName("고친 메모가 저장되고 고쳤다는 소식이 남는다")
        void editSavesAndPublishes() {
            ExplorerService.RegisteredExplorer registered = 가입();
            ExplorerId me = registered.explorer().id();
            String mapId = registered.personalMap().id().value();
            칠한다(me, JONGNO);
            checkIns.edit(new EditVisitCommand(me, null, JONGNO, LocalDate.now(clock).minusDays(2), "야간개장", null));
            assertThat(jdbc.queryForObject("SELECT memo FROM visit WHERE map_id = ?", String.class, mapId)).isEqualTo("야간개장");
            assertThat(소식(mapId)).extracting(OutboxEventEntity::eventName).containsExactly("MapCreated", "RegionVisited", "VisitEdited");
        }

        @Test
        @DisplayName("취소하면 방문 기록이 지워지고 선점이었는지와 함께 취소 소식이 남는다")
        void cancelDeletesAndPublishes() {
            ExplorerService.RegisteredExplorer registered = 가입();
            ExplorerId me = registered.explorer().id();
            String mapId = registered.personalMap().id().value();
            칠한다(me, JONGNO);
            checkIns.edit(new EditVisitCommand(me, null, JONGNO, LocalDate.now(clock).minusDays(2), "야간개장", null));

            var cancel = checkIns.cancel(me, null, JONGNO);
            assertThat(cancel.wasClaim()).isTrue();
            assertThat(방문_수(mapId)).isZero();
            assertThat(소식(mapId)).extracting(OutboxEventEntity::eventName)
                .containsExactly("MapCreated", "RegionVisited", "VisitEdited", "VisitCancelled");
        }

        @Test
        @DisplayName("이미 취소한 곳은 다시 취소할 수 없다")
        void cannotCancelTwice() {
            ExplorerId me = 탐험가();
            칠한다(me, JONGNO);
            checkIns.cancel(me, null, JONGNO);
            assertThatThrownBy(() -> checkIns.cancel(me, null, JONGNO))
                .satisfies(thrown -> assertThat(errorOf(thrown)).isEqualTo(ExplorationError.VISIT_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("개발용 샘플 영토")
    class DevSeed {

        private List<ExplorationDevService.SampleVisit> 강원_샘플() {
            return catalog.activeRegions().stream().filter(region -> region.provinceCode().equals("KR-31")).limit(12)
                .map(region -> new ExplorationDevService.SampleVisit(RegionCode.of(region.code()),
                    LocalDate.now(clock).minusMonths(3), "샘플"))
                .toList();
        }

        @Test
        @DisplayName("온보딩이 끝난 탐험가도 하루 상한 없이 채워진다")
        void seedBypassesCap() {
            ExplorerId me = 탐험가();
            clock.advance(Duration.ofHours(100));
            assertThat(dev.seed(me, 강원_샘플())).isEqualTo(12);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE checked_in_by = ?", Integer.class, me.value()))
                .isEqualTo(12);
        }

        @Test
        @DisplayName("다시 채우면 이전 샘플을 지우고 새로 채우며, 지우기는 채운 만큼 지운다")
        void reseedReplaces() {
            ExplorerId me = 탐험가();
            var samples = 강원_샘플();
            dev.seed(me, samples);
            assertThat(dev.seed(me, samples.subList(0, 3))).isEqualTo(3);
            assertThat(dev.clear(me)).isEqualTo(3);
        }
    }
}
