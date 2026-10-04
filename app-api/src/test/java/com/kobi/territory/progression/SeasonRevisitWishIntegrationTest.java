package com.kobi.territory.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.exploration.api.event.VisitsRestored;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.application.RecalculateService;
import com.kobi.territory.support.Explorers;
import com.kobi.territory.support.Explorers.Anonymous;
import com.kobi.territory.support.Explorers.Session;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 9단계 게임 요소 2순위 — 계절 한정 테마·재방문 도장·가고 싶은 곳이 체크인·도장·핀을 따라 진행·가방·친구 소식까지 이어지는 이야기
 * (MockMvc + 릴레이). 기간·해 넘김은 테스트 시계로 옮기고 끝나면 되돌린다.
 */
@IntegrationTest
@DisplayName("게임 요소 2순위 — 계절 한정 테마·재방문 도장·가고 싶은 곳")
class SeasonRevisitWishIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final String 종로구 = "KR-11010";
    private static final String 중구 = "KR-11020";
    private static final String 가평군 = "KR-31370";
    /** 가을 단풍 명소 열 곳(seasons.json). */
    private static final List<String> 단풍명소 = List.of("KR-32060", "KR-35040", "KR-32340", "KR-37330", "KR-36450", "KR-33320",
        "KR-38400", "KR-11090", "KR-31370", "KR-35310");

    @Autowired MutableClock clock;
    @Autowired Explorers explorers;
    @Autowired RecalculateService recalculate;
    @Autowired JdbcTemplate jdbc;

    private Instant 처음시각;

    @BeforeEach
    void rememberClock() {
        처음시각 = clock.instant();
    }

    @AfterEach
    void restoreClock() {
        explorers.전달이_끝날_때까지();
        clock.set(처음시각);
    }

    // ---- 준비 문장 ---------------------------------------------------------------------------------------------

    private JsonNode json(ResultActions result) throws Exception {
        return explorers.json(result);
    }

    /** 서울 시각 그 날 정오로 시계를 옮긴다. */
    private void 그날이_된다(int year, int month, int day) {
        clock.set(LocalDate.of(year, month, day).atTime(12, 0).atZone(clock.getZone()).toInstant());
    }

    /** 하루 상한(5곳)을 넘지 않게 다섯 곳마다 다음 날로 넘기며 칠한다(mapId 없으면 개인 지도). */
    private void 하루_다섯곳씩_칠한다(Anonymous who, String mapId, List<String> codes) throws Exception {
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0 && i % 5 == 0) clock.advance(Duration.ofDays(1));
            explorers.칠한다(who, mapId, codes.get(i));
        }
    }

    private JsonNode 진행(Anonymous who) throws Exception {
        return json(explorers.기기로(who, get("/progress")).andExpect(status().isOk()));
    }

    private JsonNode 계절(Anonymous who) throws Exception {
        return json(explorers.기기로(who, get("/seasons/current")).andExpect(status().isOk()));
    }

    private JsonNode 위시리스트(Anonymous who) throws Exception {
        return json(explorers.기기로(who, get("/wishlist")).andExpect(status().isOk()));
    }

    private ResultActions 다시_다녀왔어요(Anonymous who, String code) throws Exception {
        clock.advance(Duration.ofSeconds(1));
        return explorers.기기로(who, post("/revisits/" + code));
    }

    private int 장부(String explorerId, String refId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM xp_ledger WHERE explorer_id = ? AND ref_id = ?", Integer.class,
            explorerId, refId);
    }

    private int 장부_건수(String explorerId, String source) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND source = ?", Integer.class, explorerId,
            source);
    }

    private int 소식(String explorerId, String kind) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM feed_entry WHERE actor_id = ? AND kind = ?", Integer.class, explorerId, kind);
    }

    private JsonNode 가방의(Anonymous who, String itemId) throws Exception {
        for (JsonNode item : json(explorers.기기로(who, get("/inventory")).andExpect(status().isOk())).get("items")) {
            if (item.get("itemId").asText().equals(itemId)) return item;
        }
        return null;
    }

    private static boolean 칭호를_얻었다(JsonNode progress, String titleId) {
        for (JsonNode title : progress.get("titles")) {
            if (title.get("id").asText().equals(titleId)) return title.get("earned").asBoolean();
        }
        return false;
    }

    // ---- 이야기 ------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("재방문 도장")
    class Revisit {

        @Test
        @DisplayName("처음 칠한 해에는 받지 못하고 내년부터 받을 수 있다고 알려 준다")
        void sameYear() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            explorers.칠한다(me, 종로구);

            explorers.기기로(me, get("/revisits/" + 종로구)).andExpect(status().isOk())
                .andExpect(jsonPath("$.painted").value(true))
                .andExpect(jsonPath("$.canStamp").value(false))
                .andExpect(jsonPath("$.reason").value("SAME_YEAR"))
                .andExpect(jsonPath("$.firstYear").value(2026))
                .andExpect(jsonPath("$.availableFromYear").value(2027));
            다시_다녀왔어요(me, 종로구).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REVISIT_SAME_YEAR"));
        }

        @Test
        @DisplayName("다음 해에 다시 다녀오면 그 해 도장 하나와 XP·단골 표시·색 변형·친구 소식을 받고, 같은 해에는 한 번뿐이다")
        void nextYear() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            explorers.칠한다(me, 종로구);
            그날이_된다(2027, 3, 2);

            다시_다녀왔어요(me, 종로구).andExpect(status().isCreated())
                .andExpect(jsonPath("$.year").value(2027))
                .andExpect(jsonPath("$.firstYear").value(2026))
                .andExpect(jsonPath("$.xp").value(10))
                .andExpect(jsonPath("$.stampCount").value(1));

            await().atMost(WAIT).until(() -> 진행(me).get("revisitStampCount").asInt() == 1);
            assertThat(장부(me.id(), "revisit:" + me.id() + ":" + 종로구 + "@2027")).isEqualTo(10);
            await().atMost(WAIT).untilAsserted(() -> {
                JsonNode lantern = 가방의(me, "region:" + 종로구);
                assertThat(lantern.get("variant").asInt()).isEqualTo(2);
                assertThat(lantern.get("variantLook").get("primary").asText()).isEqualTo(lantern.get("look").get("secondary").asText());
            });
            await().atMost(WAIT).until(() -> 소식(me.id(), "REVISIT_STAMPED") == 1);
            explorers.기기로(me, get("/revisits")).andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.stamps[0].regionCode").value(종로구));
            다시_다녀왔어요(me, 종로구).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVISIT_ALREADY_STAMPED"));
        }

        @Test
        @DisplayName("취소하고 다시 칠하면 다시 칠한 해가 처음 칠한 해가 된다 — 취소로 도장을 앞당길 수 없다")
        void cancelRebasesFirstYear() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            explorers.칠한다(me, 종로구);
            그날이_된다(2027, 3, 5);
            explorers.기기로(me, delete("/visits/" + 종로구)).andExpect(status().isNoContent());
            explorers.칠한다(me, 종로구);

            explorers.기기로(me, get("/revisits/" + 종로구)).andExpect(jsonPath("$.firstYear").value(2027))
                .andExpect(jsonPath("$.reason").value("SAME_YEAR")).andExpect(jsonPath("$.availableFromYear").value(2028));
            다시_다녀왔어요(me, 종로구).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REVISIT_SAME_YEAR"));
        }

        @Test
        @DisplayName("아직 칠하지 않은 지역은 도장을 받을 수 없다")
        void notPainted() throws Exception {
            Anonymous me = explorers.익명_탐험가();

            explorers.기기로(me, get("/revisits/" + 가평군)).andExpect(jsonPath("$.reason").value("NOT_PAINTED"));
            다시_다녀왔어요(me, 가평군).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REVISIT_NOT_PAINTED"));
        }

        @Test
        @DisplayName("도장도 하루 체크인 상한을 함께 써서 개인 지도에 네 곳을 칠한 날은 도장 하나까지만 받는다")
        void sharesDailyCap() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            explorers.칠한다(me, 종로구);
            explorers.칠한다(me, 중구);
            그날이_된다(2027, 3, 3);
            for (String code : List.of("KR-11030", "KR-11040", "KR-11050", "KR-11060")) explorers.칠한다(me, code);

            다시_다녀왔어요(me, 종로구).andExpect(status().isCreated());

            explorers.기기로(me, get("/revisits/" + 중구)).andExpect(jsonPath("$.reason").value("DAILY_CAP"));
            다시_다녀왔어요(me, 중구).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DAILY_CAP_EXCEEDED"));
            explorers.체크인(me, Explorers.방문("KR-11070", LocalDate.now(clock), null, null))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DAILY_CAP_EXCEEDED"));
        }
    }

    @Nested
    @DisplayName("가고 싶은 곳")
    class Wishlist {

        @Test
        @DisplayName("핀을 꽂은 곳을 칠하면 다녀옴이 되고 XP 를 받으며, 다시 꽂아 또 다녀와도 XP 는 한 번이다")
        void fulfilledOnce() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            explorers.기기로(me, put("/wishlist/" + 가평군)).andExpect(status().isOk())
                .andExpect(jsonPath("$.max").value(30))
                .andExpect(jsonPath("$.pendingCount").value(1))
                .andExpect(jsonPath("$.items[0].status").value("WANTED"));

            explorers.칠한다(me, 가평군);

            await().atMost(WAIT).until(() -> "VISITED".equals(위시리스트(me).get("items").get(0).get("status").asText()));
            await().atMost(WAIT).until(() -> 진행(me).get("wishFulfilledCount").asInt() == 1);
            assertThat(장부(me.id(), "wish:" + me.id() + ":" + 가평군)).isEqualTo(20);
            explorers.기기로(me, get("/wishlist/" + 가평군)).andExpect(jsonPath("$.pinned").value(true))
                .andExpect(jsonPath("$.status").value("VISITED"));

            explorers.기기로(me, delete("/wishlist/" + 가평군)).andExpect(status().isNoContent());
            explorers.기기로(me, delete("/visits/" + 가평군)).andExpect(status().isNoContent());
            explorers.기기로(me, put("/wishlist/" + 가평군)).andExpect(status().isOk());
            explorers.칠한다(me, 가평군);
            await().atMost(WAIT).until(() -> "VISITED".equals(위시리스트(me).get("items").get(0).get("status").asText()));
            explorers.전달이_끝날_때까지();
            assertThat(장부_건수(me.id(), "WISH_FULFILLED")).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 칠한 지역에는 핀을 꽂을 수 없다")
        void paintedRefused() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            explorers.칠한다(me, 종로구);

            explorers.기기로(me, put("/wishlist/" + 종로구)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WISH_ALREADY_VISITED"));
            explorers.기기로(me, get("/wishlist/" + 종로구)).andExpect(jsonPath("$.pinned").value(false));
        }

        @Test
        @DisplayName("아직 다녀오지 않은 핀은 서른 개까지다")
        void thirtyAtMost() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            List<String> codes = new ArrayList<>();
            json(explorers.기기로(me, get("/catalog/regions"))).forEach(region -> codes.add(region.get("code").asText()));
            for (String code : codes.subList(0, 30)) explorers.기기로(me, put("/wishlist/" + code)).andExpect(status().isOk());

            explorers.기기로(me, put("/wishlist/" + codes.get(30))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WISHLIST_FULL"));
            assertThat(위시리스트(me).get("pendingCount").asInt()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("계절 한정 테마")
    class Season {

        @Test
        @DisplayName("가을 회차 기간 안에 단풍 명소 열 곳을 칠하면 완성되고 XP·칭호·회차 배경·친구 소식을 받는다 — 기간 전에 칠한 곳은 세지 않는다")
        void autumnCompleted() throws Exception {
            그날이_된다(2026, 9, 30);
            Anonymous me = explorers.익명_탐험가();
            explorers.칠한다(me, 단풍명소.getFirst());
            그날이_된다(2026, 10, 10);
            하루_다섯곳씩_칠한다(me, null, 단풍명소.subList(1, 10));
            explorers.전달이_끝날_때까지();

            JsonNode current = 계절(me).get("current").get(0);
            assertThat(current.get("roundId").asText()).isEqualTo("autumn-2026");
            assertThat(current.get("have").asInt()).isEqualTo(9);
            assertThat(current.get("completed").asBoolean()).isFalse();
            assertThat(current.get("remainingSeconds").asLong()).isPositive();

            explorers.기기로(me, delete("/visits/" + 단풍명소.getFirst())).andExpect(status().isNoContent());
            explorers.칠한다(me, 단풍명소.getFirst());

            await().atMost(WAIT).until(() -> 계절(me).get("current").get(0).get("completed").asBoolean());
            await().atMost(WAIT).until(() -> 장부(me.id(), "season:" + me.id() + ":autumn-2026") == 150);
            await().atMost(WAIT).until(() -> 칭호를_얻었다(진행(me), "season-autumn"));
            await().atMost(WAIT).until(() -> 가방의(me, "season:autumn-2026") != null);
            await().atMost(WAIT).until(() -> 소식(me.id(), "SEASON_COMPLETED") == 1);
            assertThat(계절(me).get("current").get(0).get("rewarded").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("기간이 끝나면 미완성 진행은 닫힌 기록으로 남고 다음 해 회차는 0부터 센다")
        void closesAndRestarts() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            그날이_된다(2026, 10, 10);
            explorers.칠한다(me, 단풍명소.get(0));
            explorers.칠한다(me, 단풍명소.get(1));
            explorers.전달이_끝날_때까지();

            그날이_된다(2026, 12, 5);
            explorers.칠한다(me, 단풍명소.get(2));
            explorers.전달이_끝날_때까지();
            JsonNode winter = 계절(me);
            assertThat(winter.get("current")).isEmpty();
            assertThat(winter.get("next").get("roundId").asText()).isEqualTo("spring-2027");
            assertThat(winter.get("history").get(0).get("roundId").asText()).isEqualTo("autumn-2026");
            assertThat(winter.get("history").get(0).get("have").asInt()).isEqualTo(2);

            그날이_된다(2027, 10, 5);
            explorers.칠한다(me, 단풍명소.get(3));
            explorers.전달이_끝날_때까지();
            JsonNode nextAutumn = 계절(me).get("current").get(0);
            assertThat(nextAutumn.get("roundId").asText()).isEqualTo("autumn-2027");
            assertThat(nextAutumn.get("have").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("공유 지도에서 함께 완성하면 완성 시점 멤버 모두가 받는다")
        void sharedMapEveryone() throws Exception {
            Anonymous owner = explorers.익명_탐험가();
            Anonymous friend = explorers.익명_탐험가();
            그날이_된다(2026, 10, 12);
            String mapId = explorers.공유_지도를_만든다(owner, "단풍 원정대").get("mapId").asText();
            explorers.합류한다(friend, explorers.초대코드(owner, mapId));
            하루_다섯곳씩_칠한다(owner, mapId, 단풍명소.subList(0, 5));
            하루_다섯곳씩_칠한다(friend, mapId, 단풍명소.subList(5, 10));

            await().atMost(WAIT).until(() -> 장부(owner.id(), "season:" + owner.id() + ":autumn-2026") == 150
                && 장부(friend.id(), "season:" + friend.id() + ":autumn-2026") == 150);
            explorers.기기로(owner, get("/seasons/current").param("mapId", mapId))
                .andExpect(jsonPath("$.current[0].completed").value(true))
                .andExpect(jsonPath("$.current[0].rewarded").value(true));
        }

        @Test
        @DisplayName("완성된 뒤 지도에 들어온 멤버는 그 회차 보상을 받지 않는다")
        void lateJoinerGetsNothing() throws Exception {
            그날이_된다(2026, 10, 16);
            Anonymous owner = explorers.익명_탐험가();
            String mapId = explorers.공유_지도를_만든다(owner, "늦은 합류").get("mapId").asText();
            하루_다섯곳씩_칠한다(owner, mapId, 단풍명소);
            await().atMost(WAIT).until(() -> 장부(owner.id(), "season:" + owner.id() + ":autumn-2026") == 150);
            Anonymous late = explorers.익명_탐험가();

            explorers.합류한다(late, explorers.초대코드(owner, mapId));
            explorers.전달이_끝날_때까지();

            explorers.기기로(late, get("/seasons/current").param("mapId", mapId))
                .andExpect(jsonPath("$.current[0].completed").value(true))
                .andExpect(jsonPath("$.current[0].rewarded").value(false));
            assertThat(장부(late.id(), "season:" + late.id() + ":autumn-2026")).isZero();
            assertThat(가방의(late, "season:autumn-2026")).isNull();
        }

        @Test
        @DisplayName("다시 세어도 진행·XP 가 그대로다")
        void recalculationKeepsSame() throws Exception {
            그날이_된다(2026, 10, 14);
            Anonymous me = explorers.익명_탐험가();
            하루_다섯곳씩_칠한다(me, null, 단풍명소);
            await().atMost(WAIT).until(() -> 장부(me.id(), "season:" + me.id() + ":autumn-2026") == 150);
            explorers.전달이_끝날_때까지();
            long xpBefore = 진행(me).get("xp").asLong();

            recalculate.recalculate(ExplorerId.of(me.id()));
            recalculate.recalculate(ExplorerId.of(me.id()));

            assertThat(진행(me).get("xp").asLong()).isEqualTo(xpBefore);
            assertThat(계절(me).get("current").get(0).get("have").asInt()).isEqualTo(10);
            assertThat(장부_건수(me.id(), "SEASON_COMPLETE")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("익명 탐험가를 계정으로 합치면")
    class Merge {

        @Test
        @DisplayName("도장과 가고 싶은 곳을 계정으로 합치고 합친 기록으로 다시 세어 보상을 맞춘다")
        void mergesRecords() throws Exception {
            String email = Explorers.새_이메일("merge9");
            Session account = explorers.로그인(null, email);
            Anonymous device = explorers.익명_탐험가();
            explorers.칠한다(device, 종로구);
            그날이_된다(2027, 3, 4);
            다시_다녀왔어요(device, 종로구).andExpect(status().isCreated());
            explorers.기기로(device, put("/wishlist/" + 가평군)).andExpect(status().isOk());
            explorers.전달이_끝날_때까지();

            explorers.로그인(device, email);
            explorers.전달이_끝날_때까지();

            explorers.세션으로(account, get("/revisits")).andExpect(jsonPath("$.count").value(1));
            explorers.세션으로(account, get("/wishlist")).andExpect(jsonPath("$.items[0].regionCode").value(가평군));
            await().atMost(WAIT).until(() -> 장부(account.explorerId(), "revisit:" + account.explorerId() + ":" + 종로구 + "@2027") == 10);
            List<String> variants = new ArrayList<>();
            for (JsonNode item : json(explorers.세션으로(account, get("/inventory"))).get("items")) {
                if (item.get("variant").asInt() == 2) variants.add(item.get("itemId").asText());
            }
            assertThat(variants).containsExactly("region:" + 종로구);
        }

        @Test
        @DisplayName("계정의 대기 핀 지역을 익명 시절에 칠해 두었으면 합친 뒤 다녀옴이 되고 XP 는 한 번이다")
        void pendingPinFulfilledByAbsorbedVisit() throws Exception {
            String email = Explorers.새_이메일("merge9pin");
            Session account = explorers.로그인(null, email);
            explorers.세션으로(account, put("/wishlist/" + 가평군)).andExpect(status().isOk());
            Anonymous device = explorers.익명_탐험가();
            explorers.칠한다(device, 가평군);
            explorers.전달이_끝날_때까지();

            explorers.로그인(device, email);
            explorers.전달이_끝날_때까지();

            await().atMost(WAIT).until(() -> "VISITED".equals(
                json(explorers.세션으로(account, get("/wishlist/" + 가평군))).get("status").asText()));
            await().atMost(WAIT).until(() -> 장부(account.explorerId(), "wish:" + account.explorerId() + ":" + 가평군) == 20);
            explorers.전달이_끝날_때까지();
            assertThat(장부_건수(account.explorerId(), "WISH_FULFILLED")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("지난 단계에 쌓인 소식")
    class LegacyEvents {

        @Autowired ObjectMapper om;

        @Test
        @DisplayName("방문 처리 시각이 없던 예전 재가입 복구 소식도 읽히고 계절 회차에는 다시 넣지 않는다")
        void oldVisitsRestored() throws Exception {
            VisitsRestored legacy = om.readValue("""
                {"mapId":"m","explorerId":"e","restoredRegionCodes":["KR-11010"],"regionsBackOnMap":["KR-11010"],
                 "memberIds":["e"],"restoredAt":"2026-10-04T03:00:00Z"}""", VisitsRestored.class);

            assertThat(legacy.restoredRegionCodes()).containsExactly("KR-11010");
            assertThat(legacy.restoredVisits()).isEmpty();
        }
    }
}
