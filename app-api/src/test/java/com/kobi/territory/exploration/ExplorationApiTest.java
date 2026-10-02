package com.kobi.territory.exploration;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** D3: 탐험·카탈로그·dev API 계약(MockMvc). 오류는 {code, message}. */
@IntegrationTest
class ExplorationApiTest {

    private static final String H = "X-Explorer-Token";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;

    private String me;
    private String token;
    private String myMap;
    private LocalDate today;

    @BeforeEach
    void issueExplorer() throws Exception {
        JsonNode body = json(mvc.perform(post("/explorers"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.anonymous").value(true))
            .andExpect(jsonPath("$.createdAt").exists()));
        me = body.get("explorerId").asText();
        token = body.get("accessToken").asText();
        myMap = body.get("personalMapId").asText();
        today = LocalDate.now(clock);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return om.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder) {
        return builder.header(H, token);
    }

    private ResultActions checkIn(String code, LocalDate date, String memo) throws Exception {
        String body = om.writeValueAsString(java.util.Map.of("regionCode", code, "visitDate", date.toString(),
            "memo", memo));
        return mvc.perform(as(post("/visits")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static void error(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ---- 탐험가 식별 ----

    @Test
    void 토큰_헤더가_없으면_401_REQUIRED_모르는_토큰이면_401_INVALID() throws Exception {
        error(mvc.perform(get("/territory")), 401, "EXPLORER_TOKEN_REQUIRED");
        error(mvc.perform(get("/territory").header(H, "not-a-token")), 401, "EXPLORER_TOKEN_INVALID");
        // explorerId 는 공개 식별자라 인증 수단이 아니다(결정 2)
        error(mvc.perform(get("/territory").header(H, me)), 401, "EXPLORER_TOKEN_INVALID");
        error(mvc.perform(get("/territory").header("X-Explorer-Id", me)), 401, "EXPLORER_TOKEN_REQUIRED");
        mvc.perform(as(get("/explorers/me"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.explorerId").value(me)).andExpect(jsonPath("$.personalMapId").value(myMap));
    }

    @Test
    void 다른_탐험가의_지도는_403() throws Exception {
        JsonNode other = json(mvc.perform(post("/explorers")));
        error(mvc.perform(as(get("/territory").param("mapId", other.get("personalMapId").asText()))), 403, "NOT_A_MEMBER");
        error(mvc.perform(as(get("/territory").param("mapId", "00000000-0000-0000-0000-000000000000"))), 404,
            "MAP_NOT_FOUND");
    }

    // ---- 미리보기 → 체크인 → 영토 → 수정 → 취소 왕복 ----

    @Test
    void 미리보기_체크인_영토_수정_취소_왕복() throws Exception {
        mvc.perform(as(get("/visits/preview").param("region", "KR-11010")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mapId").value(myMap))
            .andExpect(jsonPath("$.regionName").value("종로구"))
            .andExpect(jsonPath("$.provinceName").value("서울"))
            .andExpect(jsonPath("$.rarity").value("COMMON"))
            .andExpect(jsonPath("$.alreadyVisited").value(false))
            .andExpect(jsonPath("$.nth").value(1))
            .andExpect(jsonPath("$.xp.lines", hasSize(3)))
            .andExpect(jsonPath("$.xp.lines[0].label").value("일반 지역 기본"))
            .andExpect(jsonPath("$.xp.lines[1].label").value("서울 첫 발 도장"))
            .andExpect(jsonPath("$.xp.lines[2].source").value("FIRST_CLAIM"))
            .andExpect(jsonPath("$.xp.total").value(35))
            .andExpect(jsonPath("$.items[0].itemId").value("region:KR-11010"))
            .andExpect(jsonPath("$.items[0].name").value("청사초롱 등불"));

        checkIn("KR-11010", today.minusYears(1), "경복궁 야간개장")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mapId").value(myMap))
            .andExpect(jsonPath("$.visit.regionCode").value("KR-11010"))
            .andExpect(jsonPath("$.visit.visitDate").value(today.minusYears(1).toString()))
            .andExpect(jsonPath("$.visit.memo").value("경복궁 야간개장"))
            .andExpect(jsonPath("$.visit.checkedInBy").value(me))
            .andExpect(jsonPath("$.visit.verification").value("NONE"))
            .andExpect(jsonPath("$.nth").value(1))
            .andExpect(jsonPath("$.firstInProvince").value(true))
            .andExpect(jsonPath("$.firstClaim").value(true))
            .andExpect(jsonPath("$.xp.total").value(35))
            .andExpect(jsonPath("$.items[0].itemId").value("region:KR-11010"));

        checkIn("KR-37430", today, "").andExpect(status().isCreated()).andExpect(jsonPath("$.xp.total").value(75));

        mvc.perform(as(get("/territory")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mapId").value(myMap))
            .andExpect(jsonPath("$.mapKind").value("PERSONAL"))
            .andExpect(jsonPath("$.conquest.visited").value(2))
            .andExpect(jsonPath("$.conquest.total").value(250))
            .andExpect(jsonPath("$.conquest.percent").value(1))
            .andExpect(jsonPath("$.provinces", hasSize(17)))
            .andExpect(jsonPath("$.provinces[0].code").value("KR-11"))
            .andExpect(jsonPath("$.provinces[0].name").value("서울"))
            .andExpect(jsonPath("$.provinces[0].visited").value(1))
            .andExpect(jsonPath("$.provinces[0].total").value(25))
            .andExpect(jsonPath("$.visits", hasSize(2)))
            .andExpect(jsonPath("$.visits[0].regionCode").value("KR-37430")) // 방문일 최근 순
            .andExpect(jsonPath("$.visits[1].memo").value("경복궁 야간개장"));

        mvc.perform(as(get("/visits/preview").param("region", "KR-11010")))
            .andExpect(jsonPath("$.alreadyVisited").value(true)).andExpect(jsonPath("$.xp.total").value(0));
        mvc.perform(as(get("/visits/preview").param("region", "KR-11020")))
            .andExpect(jsonPath("$.nth").value(3)).andExpect(jsonPath("$.xp.total").value(20));

        mvc.perform(as(patch("/visits/KR-11010")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\":\"광화문 산책\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memo").value("광화문 산책"))
            .andExpect(jsonPath("$.visitDate").value(today.minusYears(1).toString()));
        mvc.perform(as(patch("/visits/KR-11010")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visitDate\":\"" + today.minusDays(1) + "\",\"memo\":\"\"}"))
            .andExpect(jsonPath("$.memo").value(""))
            .andExpect(jsonPath("$.visitDate").value(today.minusDays(1).toString()));

        mvc.perform(as(delete("/visits/KR-11010"))).andExpect(status().isNoContent());
        error(mvc.perform(as(delete("/visits/KR-11010"))), 404, "VISIT_NOT_FOUND");
        mvc.perform(as(get("/territory"))).andExpect(jsonPath("$.conquest.visited").value(1))
            .andExpect(jsonPath("$.visits", hasSize(1)));
    }

    // ---- 규칙 위반 오류 ----

    @Test
    void 체크인_규칙_위반은_코드별_상태로_응답한다() throws Exception {
        checkIn("KR-11010", today, "").andExpect(status().isCreated());
        error(checkIn("KR-11010", today, ""), 409, "DUPLICATE_VISIT");
        error(checkIn("KR-11020", today.plusDays(1), ""), 422, "FUTURE_VISIT_DATE");
        error(checkIn("KR-11020", today, "가".repeat(41)), 400, "MEMO_TOO_LONG");
        error(checkIn("KR-99999", today, ""), 404, "REGION_NOT_FOUND");
        error(checkIn("11010", today, ""), 400, "INVALID_REGION_CODE");
        error(mvc.perform(as(post("/visits")).contentType(MediaType.APPLICATION_JSON).content("{\"regionCode\":\"KR-11020\"}")),
            400, "VALIDATION_FAILED");
        error(mvc.perform(as(post("/visits")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"regionCode\":\"KR-11020\",\"visitDate\":\"2026-13-40\"}")), 400, "MALFORMED_REQUEST");
        error(mvc.perform(as(patch("/visits/KR-11010")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"visitDate\":\"" + today.plusDays(3) + "\"}")), 422, "FUTURE_VISIT_DATE");
        error(mvc.perform(as(patch("/visits/KR-11020")).contentType(MediaType.APPLICATION_JSON).content("{}")), 404,
            "VISIT_NOT_FOUND");
        error(mvc.perform(as(get("/visits/preview"))), 400, "BAD_PARAMETER");
    }

    @Test
    void 온보딩이_끝난_탐험가의_여섯번째_체크인은_422_DAILY_CAP_EXCEEDED() throws Exception {
        mvc.perform(as(post("/dev/explorers/age")).contentType(MediaType.APPLICATION_JSON).content("{\"hours\":73}"))
            .andExpect(status().isOk());
        String[] codes = {"KR-11010", "KR-11020", "KR-11030", "KR-11040", "KR-11050"};
        for (String code : codes) checkIn(code, today, "").andExpect(status().isCreated());
        error(checkIn("KR-11060", today, ""), 422, "DAILY_CAP_EXCEEDED");
        mvc.perform(as(get("/territory"))).andExpect(jsonPath("$.conquest.visited").value(5));
        clock.advance(Duration.ofDays(1));
        checkIn("KR-11060", LocalDate.now(clock), "").andExpect(status().isCreated());
    }

    // ---- 카탈로그 ----

    @Test
    void 카탈로그_지역_시도_아이템_GeoJSON() throws Exception {
        mvc.perform(get("/catalog/regions")).andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(250)))
            .andExpect(jsonPath("$[0].code").value("KR-11010"))
            .andExpect(jsonPath("$[0].countryCode").value("KR"))
            .andExpect(jsonPath("$[0].version").value(1));
        mvc.perform(get("/catalog/provinces")).andExpect(jsonPath("$", hasSize(17)));
        // 3단계: 아이템 정의는 DB(지역 특산물 250 + 세트 배경 9 + 운영 추가 — 같은 DB 의 다른 테스트가 추가할 수 있다)
        mvc.perform(get("/catalog/items"))
            .andExpect(jsonPath("$[?(@.grantRule == 'REGION_VISIT')]", hasSize(250)))
            .andExpect(jsonPath("$[?(@.grantRule == 'THEME_COMPLETE' && @.itemId =~ /set:.*/)]", hasSize(9)));
        mvc.perform(get("/catalog/reward-rules")).andExpect(jsonPath("$.provinceFirstBonus").value(15));
        mvc.perform(get("/catalog/regions.geojson")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/geo+json"))
            .andExpect(jsonPath("$.type").value("FeatureCollection"))
            .andExpect(jsonPath("$.features", hasSize(250)));
    }

    // ---- dev(local) ----

    @Test
    void 개발용_시드와_전부_지우기() throws Exception {
        mvc.perform(as(post("/dev/seed"))).andExpect(status().isOk()).andExpect(jsonPath("$.seeded").value(45));
        mvc.perform(as(get("/territory"))).andExpect(jsonPath("$.conquest.visited").value(45))
            .andExpect(jsonPath("$.visits[0].memo").value("뚝섬 한강")) // SAMPLE 마지막: 이번 달 1일
            .andExpect(jsonPath("$.visits[0].visitDate").value(today.withDayOfMonth(1).toString()));
        mvc.perform(as(delete("/dev/visits"))).andExpect(status().isOk()).andExpect(jsonPath("$.cleared").value(45));
        mvc.perform(as(get("/territory"))).andExpect(jsonPath("$.conquest.visited").value(0));
        error(mvc.perform(post("/dev/seed")), 401, "EXPLORER_TOKEN_REQUIRED");
    }

    @Test
    void 없는_경로와_메서드는_표준_에러() throws Exception {
        error(mvc.perform(get("/nope")), 404, "NOT_FOUND");
        error(mvc.perform(as(post("/territory"))), 405, "METHOD_NOT_ALLOWED");
        mvc.perform(get("/health")).andExpect(jsonPath("$.service").value(startsWith("territory")));
    }
}
