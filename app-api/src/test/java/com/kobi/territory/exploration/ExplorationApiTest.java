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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 1단계 D3: 탐험·카탈로그·dev API 계약(MockMvc). 오류는 {code, message}. 회귀 출처 3단계 결정 2(explorerId 는 인증 수단 아님). */
@IntegrationTest
@DisplayName("영토 화면에서 칠하고 고치고 취소하기")
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

    private ResultActions patchVisit(String code, String json) throws Exception {
        return mvc.perform(as(patch("/visits/" + code)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** 종로구(1년 전, 메모) + 울릉군(오늘)을 칠해 둔다. */
    private void 종로와_울릉을_칠해_둔다() throws Exception {
        checkIn("KR-11010", today.minusYears(1), "경복궁 야간개장").andExpect(status().isCreated());
        checkIn("KR-37430", today, "").andExpect(status().isCreated());
    }

    private static void error(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Nested
    @DisplayName("누가 요청하는지 확인할 때")
    class Identity {

        @Test
        @DisplayName("내 접근 토큰이면 내 탐험가 정보와 개인 지도를 돌려준다")
        void myTokenShowsMe() throws Exception {
            mvc.perform(as(get("/explorers/me"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.explorerId").value(me)).andExpect(jsonPath("$.personalMapId").value(myMap));
        }

        @Test
        @DisplayName("토큰 없이 요청하면 누구인지 밝히라며 거절된다")
        void noTokenIsRejected() throws Exception {
            error(mvc.perform(get("/territory")), 401, "EXPLORER_TOKEN_REQUIRED");
            error(mvc.perform(get("/territory").header("X-Explorer-Id", me)), 401, "EXPLORER_TOKEN_REQUIRED");
        }

        @Test
        @DisplayName("모르는 토큰이나 공개된 탐험가 식별자로는 들어갈 수 없다")
        void unknownTokenOrPublicIdIsRejected() throws Exception {
            error(mvc.perform(get("/territory").header(H, "not-a-token")), 401, "EXPLORER_TOKEN_INVALID");
            error(mvc.perform(get("/territory").header(H, me)), 401, "EXPLORER_TOKEN_INVALID");
        }

        @Test
        @DisplayName("다른 탐험가의 지도는 멤버가 아니라며 볼 수 없다")
        void othersMapIsForbidden() throws Exception {
            JsonNode other = json(mvc.perform(post("/explorers")));
            error(mvc.perform(as(get("/territory").param("mapId", other.get("personalMapId").asText()))), 403, "NOT_A_MEMBER");
        }

        @Test
        @DisplayName("없는 지도는 없다고 알린다")
        void unknownMapIsNotFound() throws Exception {
            error(mvc.perform(as(get("/territory").param("mapId", "00000000-0000-0000-0000-000000000000"))), 404,
                "MAP_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("칠하기 전부터 취소까지")
    class RoundTrip {

        @Test
        @DisplayName("칠하기 전 미리보기는 지역 이름·희귀도·받을 경험치 내역·받을 아이템을 보여 준다")
        void previewBeforeCheckIn() throws Exception {
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
        }

        @Test
        @DisplayName("칠하면 과거 방문일·메모가 담긴 방문 기록과 받은 보상이 돌아온다")
        void checkInReturnsVisitAndRewards() throws Exception {
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
        }

        @Test
        @DisplayName("내 영토는 정복률·시·도별 집계와 최근 방문일 순 목록을 보여 준다")
        void territoryShowsConquest() throws Exception {
            종로와_울릉을_칠해_둔다();
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
                .andExpect(jsonPath("$.visits[0].regionCode").value("KR-37430"))
                .andExpect(jsonPath("$.visits[1].memo").value("경복궁 야간개장"));
        }

        @Test
        @DisplayName("이미 칠한 곳의 미리보기는 보상이 없고, 다음 곳은 회차가 오른 채 남은 보상만 보여 준다")
        void previewAfterCheckIn() throws Exception {
            종로와_울릉을_칠해_둔다();
            mvc.perform(as(get("/visits/preview").param("region", "KR-11010")))
                .andExpect(jsonPath("$.alreadyVisited").value(true)).andExpect(jsonPath("$.xp.total").value(0));
            mvc.perform(as(get("/visits/preview").param("region", "KR-11020")))
                .andExpect(jsonPath("$.nth").value(3)).andExpect(jsonPath("$.xp.total").value(20));
        }

        @Test
        @DisplayName("메모만 고치면 방문일은 그대로이고, 방문일과 빈 메모로도 고칠 수 있다")
        void editMemoAndDate() throws Exception {
            종로와_울릉을_칠해_둔다();
            patchVisit("KR-11010", "{\"memo\":\"광화문 산책\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memo").value("광화문 산책"))
                .andExpect(jsonPath("$.visitDate").value(today.minusYears(1).toString()));
            patchVisit("KR-11010", "{\"visitDate\":\"" + today.minusDays(1) + "\",\"memo\":\"\"}")
                .andExpect(jsonPath("$.memo").value(""))
                .andExpect(jsonPath("$.visitDate").value(today.minusDays(1).toString()));
        }

        @Test
        @DisplayName("취소하면 영토에서 빠지고 같은 곳을 다시 취소할 수는 없다")
        void cancelRemovesFromTerritory() throws Exception {
            종로와_울릉을_칠해_둔다();
            mvc.perform(as(delete("/visits/KR-11010"))).andExpect(status().isNoContent());
            error(mvc.perform(as(delete("/visits/KR-11010"))), 404, "VISIT_NOT_FOUND");
            mvc.perform(as(get("/territory"))).andExpect(jsonPath("$.conquest.visited").value(1))
                .andExpect(jsonPath("$.visits", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("체크인이 규칙에 어긋나면")
    class Rejections {

        @Test
        @DisplayName("이미 칠한 곳은 다시 칠할 수 없다")
        void duplicate() throws Exception {
            checkIn("KR-11010", today, "").andExpect(status().isCreated());
            error(checkIn("KR-11010", today, ""), 409, "DUPLICATE_VISIT");
        }

        @Test
        @DisplayName("미래 날짜로는 칠할 수 없다")
        void futureDate() throws Exception {
            error(checkIn("KR-11020", today.plusDays(1), ""), 422, "FUTURE_VISIT_DATE");
        }

        @Test
        @DisplayName("메모는 40자를 넘길 수 없다")
        void memoTooLong() throws Exception {
            error(checkIn("KR-11020", today, "가".repeat(41)), 400, "MEMO_TOO_LONG");
        }

        @Test
        @DisplayName("없는 지역은 칠할 수 없다")
        void unknownRegion() throws Exception {
            error(checkIn("KR-99999", today, ""), 404, "REGION_NOT_FOUND");
        }

        @Test
        @DisplayName("지역 코드 형식이 틀리면 칠할 수 없다")
        void malformedRegionCode() throws Exception {
            error(checkIn("11010", today, ""), 400, "INVALID_REGION_CODE");
        }

        @Test
        @DisplayName("방문일이 빠지면 입력을 확인하라고 알린다")
        void missingDate() throws Exception {
            error(mvc.perform(as(post("/visits")).contentType(MediaType.APPLICATION_JSON).content("{\"regionCode\":\"KR-11020\"}")),
                400, "VALIDATION_FAILED");
        }

        @Test
        @DisplayName("있을 수 없는 날짜는 읽을 수 없는 요청으로 알린다")
        void impossibleDate() throws Exception {
            error(mvc.perform(as(post("/visits")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"regionCode\":\"KR-11020\",\"visitDate\":\"2026-13-40\"}")), 400, "MALFORMED_REQUEST");
        }

        @Test
        @DisplayName("칠한 곳의 방문일도 미래로 고칠 수 없다")
        void editToFutureDate() throws Exception {
            checkIn("KR-11010", today, "").andExpect(status().isCreated());
            error(patchVisit("KR-11010", "{\"visitDate\":\"" + today.plusDays(3) + "\"}"), 422, "FUTURE_VISIT_DATE");
        }

        @Test
        @DisplayName("칠하지 않은 곳은 고칠 수 없다")
        void editUnvisited() throws Exception {
            error(patchVisit("KR-11020", "{}"), 404, "VISIT_NOT_FOUND");
        }

        @Test
        @DisplayName("지역을 고르지 않은 미리보기는 거절된다")
        void previewWithoutRegion() throws Exception {
            error(mvc.perform(as(get("/visits/preview"))), 400, "BAD_PARAMETER");
        }
    }

    @Nested
    @DisplayName("온보딩이 끝난 탐험가")
    class DailyCap {

        @Test
        @DisplayName("하루 여섯 번째 체크인은 하루 상한에 걸리고 영토에는 다섯 곳만 남는다")
        void sixthIsRejected() throws Exception {
            mvc.perform(as(post("/dev/explorers/age")).contentType(MediaType.APPLICATION_JSON).content("{\"hours\":73}"))
                .andExpect(status().isOk());
            for (String code : new String[] {"KR-11010", "KR-11020", "KR-11030", "KR-11040", "KR-11050"}) {
                checkIn(code, today, "").andExpect(status().isCreated());
            }
            error(checkIn("KR-11060", today, ""), 422, "DAILY_CAP_EXCEEDED");
            mvc.perform(as(get("/territory"))).andExpect(jsonPath("$.conquest.visited").value(5));
        }

        @Test
        @DisplayName("다음 날에는 다시 칠할 수 있다")
        void nextDayAllowed() throws Exception {
            mvc.perform(as(post("/dev/explorers/age")).contentType(MediaType.APPLICATION_JSON).content("{\"hours\":73}"))
                .andExpect(status().isOk());
            for (String code : new String[] {"KR-11010", "KR-11020", "KR-11030", "KR-11040", "KR-11050"}) {
                checkIn(code, today, "").andExpect(status().isCreated());
            }
            clock.advance(Duration.ofDays(1));
            checkIn("KR-11060", LocalDate.now(clock), "").andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("지역·아이템 안내")
    class Catalog {

        @Test
        @DisplayName("지역 250곳을 국가 코드·판 번호와 함께 보여 준다")
        void regions() throws Exception {
            mvc.perform(get("/catalog/regions")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(250)))
                .andExpect(jsonPath("$[0].code").value("KR-11010"))
                .andExpect(jsonPath("$[0].countryCode").value("KR"))
                .andExpect(jsonPath("$[0].version").value(1));
        }

        @Test
        @DisplayName("시·도 17곳을 보여 준다")
        void provinces() throws Exception {
            mvc.perform(get("/catalog/provinces")).andExpect(jsonPath("$", hasSize(17)));
        }

        @Test
        @DisplayName("아이템은 지역 특산물 250개와 테마 완성 배경 9개를 지급 규칙과 함께 보여 준다")
        void items() throws Exception {
            mvc.perform(get("/catalog/items"))
                .andExpect(jsonPath("$[?(@.grantRule == 'REGION_VISIT')]", hasSize(250)))
                .andExpect(jsonPath("$[?(@.grantRule == 'THEME_COMPLETE' && @.itemId =~ /set:.*/)]", hasSize(9)));
        }

        @Test
        @DisplayName("보상 규칙에는 시·도 첫 발 보너스 15가 있다")
        void rewardRules() throws Exception {
            mvc.perform(get("/catalog/reward-rules")).andExpect(jsonPath("$.provinceFirstBonus").value(15));
        }

        @Test
        @DisplayName("지도 모양은 250개 지역의 지리 정보로 내려간다")
        void geoJson() throws Exception {
            mvc.perform(get("/catalog/regions.geojson")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/geo+json"))
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features", hasSize(250)));
        }
    }

    @Nested
    @DisplayName("개발용 샘플")
    class DevSeed {

        @Test
        @DisplayName("샘플 45곳을 채우고 전부 지울 수 있다")
        void seedAndClear() throws Exception {
            mvc.perform(as(post("/dev/seed"))).andExpect(status().isOk()).andExpect(jsonPath("$.seeded").value(45));
            mvc.perform(as(get("/territory"))).andExpect(jsonPath("$.conquest.visited").value(45))
                .andExpect(jsonPath("$.visits[0].memo").value("뚝섬 한강"))
                .andExpect(jsonPath("$.visits[0].visitDate").value(today.withDayOfMonth(1).toString()));
            mvc.perform(as(delete("/dev/visits"))).andExpect(status().isOk()).andExpect(jsonPath("$.cleared").value(45));
            mvc.perform(as(get("/territory"))).andExpect(jsonPath("$.conquest.visited").value(0));
        }

        @Test
        @DisplayName("누구의 영토인지 모르면 채우지 않는다")
        void seedNeedsExplorer() throws Exception {
            error(mvc.perform(post("/dev/seed")), 401, "EXPLORER_TOKEN_REQUIRED");
        }
    }

    @Nested
    @DisplayName("엉뚱한 요청")
    class Misc {

        @Test
        @DisplayName("없는 주소는 같은 형식의 오류로 알린다")
        void unknownPath() throws Exception {
            error(mvc.perform(get("/nope")), 404, "NOT_FOUND");
        }

        @Test
        @DisplayName("허용하지 않는 요청 방식은 같은 형식의 오류로 알린다")
        void wrongMethod() throws Exception {
            error(mvc.perform(as(post("/territory"))), 405, "METHOD_NOT_ALLOWED");
        }

        @Test
        @DisplayName("상태 확인은 서비스 이름을 알려 준다")
        void healthNamesService() throws Exception {
            mvc.perform(get("/health")).andExpect(jsonPath("$.service").value(startsWith("territory")));
        }
    }
}
