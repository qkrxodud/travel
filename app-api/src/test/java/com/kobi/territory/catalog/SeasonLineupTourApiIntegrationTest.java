package com.kobi.territory.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kobi.territory.catalog.application.SeasonLineupCache;
import com.kobi.territory.catalog.application.SeasonLineupService;
import com.kobi.territory.dev.DevTourApiFixtures;
import com.kobi.territory.support.Explorers;
import com.kobi.territory.support.Explorers.Anonymous;
import com.kobi.territory.support.IntegrationTestConfig;
import com.kobi.territory.support.MutableClock;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 계절 명소 근거 — TourAPI 키가 있을 때. 실제 키 없이 가짜 TourAPI(공식 문서 응답 모양, {@link DevTourApiFixtures} — 축제는 "[개발용]" 가짜)를
 * 띄우고 서비스 주소를 그쪽으로 바꾼다. 하루 호출 상한은 5회로 낮췄다. 시계는 2026-10-02(가을 회차 진행 중)에서 시작한다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:territory-tourapi;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "territory.tourapi.service-key=fixture+key/==",
    "territory.tourapi.retry-backoff=0s",
    "territory.tourapi.daily-call-limit=5",
    "territory.tourapi.collect.on-startup=false",
    "territory.tourapi.collect.cron=-"})
@AutoConfigureMockMvc
@Import({IntegrationTestConfig.class, Explorers.class})
@DisplayName("계절 명소 근거 — 키가 있을 때")
class SeasonLineupTourApiIntegrationTest {

    private static final String ADMIN = "X-Admin-Token";
    private static final String TOKEN = "local-admin-token";
    private static final HttpServer 가짜_TourAPI = start();
    private static final AtomicInteger 호출 = new AtomicInteger();
    private static final AtomicBoolean 키를_거절 = new AtomicBoolean();
    private static final AtomicBoolean 관광지_없음 = new AtomicBoolean();
    /** 단풍 순위 열 곳: 축제 여섯(정읍 12일·청송 7일·속초·장성 6일(코드 순)·평창·가평) → 관광지만 넷(태백·영월·보은(주소만)·합천, 코드 순). */
    private static final List<String> 단풍_근거_열곳 = List.of("KR-35040", "KR-37330", "KR-32060", "KR-36450", "KR-32340", "KR-31370",
        "KR-32050", "KR-32330", "KR-33320", "KR-38400");
    private static final Map<String, String> 받은_키 = new HashMap<>();

    /** 벚꽃 축제 순위 열 곳(가짜 축제): 진해·경주(두 건) → 영등포 → 송파·강릉(7일 동률, 코드 순) → 하동 → 춘천·충주(주소만)·구례·제주(3일, 코드 순). */
    private static final List<String> 벚꽃_근거_열곳 = List.of("KR-38115", "KR-37020", "KR-11190", "KR-11240", "KR-32030", "KR-38360",
        "KR-32010", "KR-33020", "KR-36330", "KR-39010");

    @Autowired MockMvc mvc;
    @Autowired Explorers explorers;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired SeasonLineupService lineups;
    @Autowired SeasonLineupCache cache;

    private Instant 처음시각;

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/KorService2/searchKeyword2", exchange -> {
                호출.incrementAndGet();
                Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
                String body = DevTourApiFixtures.searchKeyword(키를_거절.get() ? DevTourApiFixtures.KEY_REJECTED : query.get("serviceKey"),
                    관광지_없음.get() ? "" : query.get("keyword"), Integer.parseInt(query.get("numOfRows")), Integer.parseInt(query.get("pageNo")));
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.createContext("/KorService2/searchFestival2", exchange -> {
                호출.incrementAndGet();
                Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
                synchronized (받은_키) {
                    받은_키.put("serviceKey", query.get("serviceKey"));
                }
                String body = DevTourApiFixtures.searchFestival(키를_거절.get() ? DevTourApiFixtures.KEY_REJECTED : query.get("serviceKey"),
                    query.get("eventStartDate"), query.get("eventEndDate"), Integer.parseInt(query.get("numOfRows")),
                    Integer.parseInt(query.get("pageNo")));
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> query = new HashMap<>();
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            query.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return query;
    }

    @DynamicPropertySource
    static void tourApiAddress(DynamicPropertyRegistry registry) {
        registry.add("territory.tourapi.base-url", () -> "http://127.0.0.1:" + 가짜_TourAPI.getAddress().getPort() + "/KorService2");
    }

    @BeforeEach
    void reset() {
        처음시각 = clock.instant();
        jdbc.update("DELETE FROM season_lineup_region WHERE round_id NOT IN ('spring-2026', 'autumn-2026')");
        jdbc.update("DELETE FROM season_lineup WHERE round_id NOT IN ('spring-2026', 'autumn-2026')");
        jdbc.update("DELETE FROM tourapi_response");
        jdbc.update("DELETE FROM tourapi_usage");
        cache.invalidate();
        호출.set(0);
        키를_거절.set(false);
        관광지_없음.set(false);
    }

    @AfterEach
    void restoreClock() {
        explorers.전달이_끝날_때까지();
        clock.set(처음시각);
        cache.invalidate();
    }

    private JsonNode 관리자(MockHttpServletRequestBuilder request) throws Exception {
        return explorers.json(mvc.perform(request.header(ADMIN, TOKEN)).andExpect(status().isOk()));
    }

    private JsonNode 봄_갱신() throws Exception {
        return 관리자(post("/admin/seasons/spring-2027/refresh"));
    }

    private void 그날이_된다(int year, int month, int day) {
        clock.set(LocalDate.of(year, month, day).atTime(12, 0).atZone(clock.getZone()).toInstant());
    }

    @Nested
    @DisplayName("관리자가 다음 회차를 갱신하면")
    class Refresh {

        @Test
        @DisplayName("축제가 많은 지역 열 곳이 근거와 함께 후보로 보이고, 확정 전까지 쓰는 목록은 그대로다")
        void candidatePreview() throws Exception {
            JsonNode 회차 = 봄_갱신();

            JsonNode 후보 = 회차.get("candidate");
            assertThat(후보.get("regions").findValuesAsText("code")).containsExactlyElementsOf(벚꽃_근거_열곳);
            assertThat(후보.get("provenance").asText()).isEqualTo("tourapi");
            assertThat(후보.get("source").asText()).isEqualTo("한국관광공사 TourAPI");
            assertThat(후보.get("evidencedRegions").asInt()).isEqualTo(10);
            JsonNode 진해 = 후보.get("regions").get(0);
            assertThat(진해.get("evidence").findValuesAsText("title")).containsExactly("[개발용] 진해 벚꽃 축제", "[개발용] 진해 벚꽃 야행");
            assertThat(진해.get("evidence").get(0).get("contentId").asText()).isNotBlank();
            assertThat(진해.get("evidence").get(0).get("fetchedAt").asText()).isNotBlank();
            assertThat(회차.get("lastAttempt").get("outcome").asText()).isEqualTo("COLLECTED");
            assertThat(회차.get("inEffect").get("provenance").asText()).isEqualTo("ai-estimate");
        }

        @Test
        @DisplayName("회차 하나를 축제 한 번 + 관광지 검색어마다 한 번(봄은 둘)으로 읽고, 같은 날 다시 갱신하면 저장해 둔 응답을 쓴다")
        void callsThenCache() throws Exception {
            봄_갱신();
            봄_갱신();

            assertThat(호출.get()).isEqualTo(2);
            assertThat(관리자(get("/admin/seasons")).get("tourApi").get("callsToday").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("새로 읽기를 고르면 다시 부른다(같은 예산 안에서)")
        void fresh() throws Exception {
            봄_갱신();
            관리자(post("/admin/seasons/spring-2027/refresh").param("fresh", "true"));

            assertThat(호출.get()).isEqualTo(4);
        }

        @Test
        @DisplayName("공공데이터포털에서 받은 원문 키를 넣으면 기관이 받는 키도 원문 그대로다")
        void keyEncodedOnce() throws Exception {
            봄_갱신();

            synchronized (받은_키) {
                assertThat(받은_키.get("serviceKey")).isEqualTo("fixture+key/==");
            }
        }

        @Test
        @DisplayName("축제가 모자란 가을은 계절 관광지 근거로 열 곳을 채운다 — 축제 지역이 앞서고 근거 종류가 구분된다")
        void attractionsFill() throws Exception {
            JsonNode 회차 = 관리자(post("/admin/seasons/autumn-2027/refresh"));

            JsonNode 후보 = 회차.get("candidate");
            assertThat(후보.get("regions").findValuesAsText("code")).containsExactlyElementsOf(단풍_근거_열곳);
            assertThat(후보.get("evidencedRegions").asInt()).isEqualTo(10);
            assertThat(후보.get("regions").get(0).get("evidence").findValuesAsText("evidenceKind")).containsExactly("FESTIVAL", "ATTRACTION");
            JsonNode 영월 = 후보.get("regions").get(7);
            assertThat(영월.get("evidence").get(0).get("evidenceKind").asText()).isEqualTo("ATTRACTION");
            assertThat(영월.get("evidence").get(0).get("startDate").isNull()).isTrue();
            assertThat(회차.get("lastAttempt").get("outcome").asText()).isEqualTo("COLLECTED");
        }

        @Test
        @DisplayName("봄은 축제 지역 열 곳이 차서 관광지만 있는 지역(제천)은 들지 않는다")
        void festivalPriority() throws Exception {
            assertThat(봄_갱신().get("candidate").get("regions").findValuesAsText("code")).doesNotContain("KR-33030");
        }

        @Test
        @DisplayName("관광지도 없어 근거가 모자라면(단풍 여섯 곳) 나머지를 AI 추정으로 채우고 경고한다")
        void partial() throws Exception {
            관광지_없음.set(true);
            JsonNode 회차 = 관리자(post("/admin/seasons/autumn-2027/refresh"));

            assertThat(회차.get("candidate").get("evidencedRegions").asInt()).isEqualTo(6);
            assertThat(회차.get("candidate").get("provenance").asText()).isEqualTo("mixed");
            assertThat(회차.get("lastAttempt").get("outcome").asText()).isEqualTo("PARTIAL");
            assertThat(회차.get("warnings").get(0).asText()).contains("6곳이라 나머지 4곳은 AI 추정");
        }
    }

    @Nested
    @DisplayName("확정하면")
    class Confirm {

        @Test
        @DisplayName("그 회차에 쓰는 목록이 되고, 회차가 열리면 탐험가 화면에 근거와 출처가 보인다")
        void shownToExplorers() throws Exception {
            봄_갱신();
            JsonNode 확정 = 관리자(put("/admin/seasons/spring-2027/confirm"));
            assertThat(확정.get("inEffect").get("confirmedBy").asText()).isEqualTo("ADMIN");
            assertThat(확정.get("inEffect").get("provenance").asText()).isEqualTo("tourapi");

            그날이_된다(2027, 3, 21);
            Anonymous 탐험가 = explorers.익명_탐험가();
            JsonNode 봄 = explorers.json(explorers.기기로(탐험가, get("/seasons/current")).andExpect(status().isOk())).get("current").get(0);

            assertThat(봄.get("roundId").asText()).isEqualTo("spring-2027");
            assertThat(봄.get("provenance").asText()).isEqualTo("tourapi");
            assertThat(봄.get("source").asText()).isEqualTo("한국관광공사 TourAPI");
            assertThat(봄.get("regions").findValuesAsText("code")).containsExactlyElementsOf(벚꽃_근거_열곳);
            assertThat(봄.get("regions").get(7).get("evidence").get(0).get("title").asText()).isEqualTo("[개발용] 충주 벚꽃 축제");
        }

        @Test
        @DisplayName("진행은 확정 목록으로 센다 — 새로 든 지역은 세고, 빠진 기본 지역은 세지 않는다")
        void progressionUsesConfirmedLineup() throws Exception {
            봄_갱신();
            관리자(put("/admin/seasons/spring-2027/confirm"));
            그날이_된다(2027, 3, 21);
            Anonymous 탐험가 = explorers.익명_탐험가();

            explorers.칠한다(탐험가, "KR-32010");
            explorers.칠한다(탐험가, "KR-33030");
            explorers.전달이_끝날_때까지();

            explorers.기기로(탐험가, get("/seasons/current")).andExpect(jsonPath("$.current[0].have").value(1))
                .andExpect(jsonPath("$.current[0].total").value(10))
                .andExpect(jsonPath("$.current[0].regions[6].code").value("KR-32010"))
                .andExpect(jsonPath("$.current[0].regions[6].collected").value(true));
        }

        @Test
        @DisplayName("회차가 열리면 다시 갱신하거나 확정할 수 없다")
        void lockedAfterStart() throws Exception {
            봄_갱신();
            그날이_된다(2027, 3, 20);

            mvc.perform(post("/admin/seasons/spring-2027/refresh").header(ADMIN, TOKEN)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_ROUND_LOCKED"));
            mvc.perform(put("/admin/seasons/spring-2027/confirm").header(ADMIN, TOKEN)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_ROUND_LOCKED"));
            assertThat(호출.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("확정 없이 회차가 열리면 그때의 기본 목록(AI 추정)으로 고정되고 남은 후보는 버린다")
        void snapshotWhenOpenedWithoutConfirmation() throws Exception {
            봄_갱신();
            그날이_된다(2027, 3, 21);

            JsonNode 봄 = 관리자(get("/admin/seasons/spring-2027"));
            assertThat(봄.get("inEffect").get("confirmedBy").asText()).isEqualTo("OPENING");
            assertThat(봄.get("inEffect").get("provenance").asText()).isEqualTo("ai-estimate");
            assertThat(봄.get("candidate").isNull()).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM season_lineup_region WHERE round_id = 'spring-2027' AND stage = 'CONFIRMED'",
                Integer.class)).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("실패하면")
    class Failure {

        @Test
        @DisplayName("키가 거절돼도 확정된 목록은 그대로이고 관리자 화면에 까닭이 보인다")
        void keyRejected() throws Exception {
            봄_갱신();
            관리자(put("/admin/seasons/spring-2027/confirm"));
            키를_거절.set(true);

            JsonNode 회차 = 관리자(post("/admin/seasons/spring-2027/refresh").param("fresh", "true"));

            assertThat(회차.get("lastAttempt").get("outcome").asText()).isEqualTo("KEY_REJECTED");
            assertThat(회차.get("inEffect").get("provenance").asText()).isEqualTo("tourapi");
            assertThat(회차.get("warnings").get(0).asText()).contains("서비스 키를 거절");
            assertThat(회차.toString()).doesNotContain("fixture+key").doesNotContain("fixture%2Bkey");
        }

        @Test
        @DisplayName("하루 호출 상한에 닿으면 그날은 더 부르지 않고 관리자에게 알린다")
        void dailyBudget() throws Exception {
            for (int i = 0; i < 2; i++) 관리자(post("/admin/seasons/spring-2027/refresh").param("fresh", "true"));

            JsonNode 회차 = 관리자(post("/admin/seasons/spring-2027/refresh").param("fresh", "true"));
            JsonNode 화면 = 관리자(get("/admin/seasons"));

            assertThat(호출.get()).isEqualTo(5);
            assertThat(회차.get("lastAttempt").get("outcome").asText()).isEqualTo("QUOTA_EXCEEDED");
            assertThat(화면.get("tourApi").get("exhausted").asBoolean()).isTrue();
            assertThat(화면.get("tourApi").get("warnings").get(0).asText()).contains("호출 상한(5회)");
            assertThat(jdbc.queryForObject("SELECT calls FROM tourapi_usage", Integer.class)).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("자동 수집은")
    class Automatic {

        @Test
        @DisplayName("키를 넣고 처음 돌면 수집 기간 전이라도 다음 회차 후보를 미리 모은다 — 확정은 하지 않는다")
        void previewAfterKeyIsSet() throws Exception {
            assertThat(lineups.collectAutomatically()).containsKeys("spring-2027", "autumn-2027");

            JsonNode 화면 = 관리자(get("/admin/seasons"));
            JsonNode 봄 = 화면.get("rounds").get(1);
            assertThat(봄.get("candidate").get("evidencedRegions").asInt()).isEqualTo(10);
            assertThat(봄.get("inEffect").get("confirmedBy").isNull()).isTrue();
        }

        @Test
        @DisplayName("같은 날 다시 돌아도 같은 회차를 다시 모으지 않는다")
        void oncePerDay() {
            lineups.collectAutomatically();
            int calls = 호출.get();

            assertThat(lineups.collectAutomatically()).isEmpty();
            assertThat(호출.get()).isEqualTo(calls);
        }

        @Test
        @DisplayName("시작 30일 전부터는 근거가 열 곳이면 자동 확정하고, 모자라면 후보로만 두고 지금 목록을 지킨다")
        void autoConfirmInWindow() throws Exception {
            그날이_된다(2027, 2, 25);
            lineups.collectAutomatically();
            JsonNode 봄 = 관리자(get("/admin/seasons/spring-2027"));
            assertThat(봄.get("inEffect").get("confirmedBy").asText()).isEqualTo("AUTO");
            assertThat(봄.get("inEffect").get("provenance").asText()).isEqualTo("tourapi");

            관광지_없음.set(true);
            그날이_된다(2027, 9, 5);
            lineups.collectAutomatically();
            JsonNode 가을 = 관리자(get("/admin/seasons/autumn-2027"));
            assertThat(가을.get("inEffect").get("provenance").asText()).isEqualTo("ai-estimate");
            assertThat(가을.get("candidate").get("evidencedRegions").asInt()).isEqualTo(6);
        }

        @Test
        @DisplayName("관리자가 확정한 회차는 자동 수집이 덮지 않는다")
        void adminWins() throws Exception {
            관리자(post("/admin/seasons/autumn-2027/refresh"));
            관리자(put("/admin/seasons/autumn-2027/confirm"));
            그날이_된다(2027, 9, 5);

            assertThat(lineups.collectAutomatically()).doesNotContainKey("autumn-2027");
            assertThat(관리자(get("/admin/seasons/autumn-2027")).get("inEffect").get("confirmedBy").asText()).isEqualTo("ADMIN");
        }
    }
}
