package com.kobi.territory.wardrobe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * D3: 꾸미기 API 계약(MockMvc) — GET /inventory, PUT /inventory/{itemId}/favorite, GET·PUT /scene, POST /admin/items,
 * GET /catalog/items(DB 기준).
 */
@IntegrationTest
class WardrobeApiTest {

    static final String ADMIN = "X-Admin-Token";
    static final String ADMIN_TOKEN = "local-admin-token";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    private String credential;

    @BeforeEach
    void issue() throws Exception {
        JsonNode body = json(mvc.perform(post("/explorers")).andExpect(status().isCreated()));
        credential = body.has("accessToken") ? body.get("accessToken").asText() : body.get("explorerId").asText();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return om.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder) {
        return builder.header(CurrentExplorer.HEADER, credential);
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return mvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)));
    }

    private void checkIn(String code) throws Exception {
        send(as(post("/visits")), Map.of("regionCode", code, "visitDate", LocalDate.now(clock).toString()))
            .andExpect(status().isCreated());
    }

    private JsonNode inventory() throws Exception {
        return json(mvc.perform(as(get("/inventory"))).andExpect(status().isOk()));
    }

    private JsonNode scene() throws Exception {
        return json(mvc.perform(as(get("/scene"))).andExpect(status().isOk()));
    }

    private static void error(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void 빈_가방과_기본_장면() throws Exception {
        assertThat(inventory().get("count").asInt()).isZero();
        JsonNode scene = scene();
        assertThat(scene.get("gender").asText()).isEqualTo("M");
        assertThat(scene.get("stylePoints").asInt()).isZero();
        assertThat(scene.get("slots").has("HAT")).isTrue();
        assertThat(scene.get("slots").get("HAT").isNull()).isTrue();
        assertThat(scene.get("props")).isEmpty();
    }

    @Test
    void 체크인하면_가방에_아이템과_자동_착용_꾸미기_점수는_서버_계산() throws Exception {
        checkIn("KR-11010"); // 청사초롱 등불 HAND 일반(1)
        checkIn("KR-36330"); // 구례 산수유 비니 HAT 희귀(3)
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(scene().get("stylePoints").asInt()).isEqualTo(4));

        JsonNode inventory = inventory();
        assertThat(inventory.get("count").asInt()).isEqualTo(2);
        JsonNode first = inventory.get("items").get(0);
        assertThat(first.get("itemId").asText()).isIn("region:KR-11010", "region:KR-36330");
        assertThat(first.get("name").asText()).isNotBlank();
        assertThat(first.get("source").asText()).isEqualTo("REGION");
        assertThat(first.get("equipped").asBoolean()).isTrue();
        JsonNode scene = scene();
        assertThat(scene.get("slots").get("HAND").get("itemId").asText()).isEqualTo("region:KR-11010");
        assertThat(scene.get("slots").get("HAT").get("tier").asText()).isEqualTo("RARE");
        assertThat(scene.get("wornCount").asInt()).isEqualTo(2);
    }

    @Test
    void 장면_편집_착용_해제_성별_장식_검증() throws Exception {
        checkIn("KR-11010");
        checkIn("KR-21090"); // 해운대 파라솔 PROP
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(inventory().get("count").asInt()).isEqualTo(2));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(scene().get("props")).hasSize(1));

        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("gender", "F");
        edit.put("unequip", List.of("HAND"));
        edit.put("props", List.of());
        JsonNode changed = json(send(as(put("/scene")), edit).andExpect(status().isOk()));
        assertThat(changed.get("gender").asText()).isEqualTo("F");
        assertThat(changed.get("slots").get("HAND").isNull()).isTrue();
        assertThat(changed.get("props")).isEmpty();
        assertThat(changed.get("stylePoints").asInt()).isZero();

        JsonNode equipped = json(send(as(put("/scene")), Map.of("equip", Map.of("HAND", "region:KR-11010"),
            "props", List.of("region:KR-21090"))).andExpect(status().isOk()));
        assertThat(equipped.get("slots").get("HAND").get("name").asText()).isEqualTo("청사초롱 등불");
        assertThat(equipped.get("props").get(0).get("itemId").asText()).isEqualTo("region:KR-21090");
        assertThat(equipped.get("gender").asText()).isEqualTo("F");

        error(send(as(put("/scene")), Map.of("equip", Map.of("HAT", "region:KR-11010"))), 422, "SLOT_MISMATCH");
        error(send(as(put("/scene")), Map.of("equip", Map.of("HAND", "region:KR-11030"))), 422, "ITEM_NOT_OWNED");
        error(send(as(put("/scene")), Map.of("equip", Map.of("HAND", "region:KR-99999"))), 404, "ITEM_NOT_FOUND");
        error(send(as(put("/scene")), Map.of("props", List.of("region:KR-21090", "region:KR-21090"))), 400, "DUPLICATE_PROP");
        error(send(as(put("/scene")), Map.of("equip", Map.of("WING", "region:KR-11010"))), 400, "MALFORMED_REQUEST");
        error(send(as(put("/scene")), Map.of("gender", "X")), 400, "MALFORMED_REQUEST");
    }

    @Test
    void 즐겨찾기() throws Exception {
        checkIn("KR-11010");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(inventory().get("count").asInt()).isEqualTo(1));

        JsonNode item = json(send(as(put("/inventory/region:KR-11010/favorite")), Map.of("favorite", true))
            .andExpect(status().isOk()));
        assertThat(item.get("favorite").asBoolean()).isTrue();
        assertThat(inventory().get("items").get(0).get("favorite").asBoolean()).isTrue();

        error(send(as(put("/inventory/region:KR-26010/favorite")), Map.of("favorite", true)), 422, "ITEM_NOT_OWNED");
        error(send(as(put("/inventory/region:KR-11010/favorite")), Map.of()), 400, "VALIDATION_FAILED");
    }

    @Test
    void 취소하면_가방에서_빠지고_벗겨진다() throws Exception {
        checkIn("KR-11010");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(scene().get("wornCount").asInt()).isEqualTo(1));
        mvc.perform(as(delete("/visits/KR-11010"))).andExpect(status().is2xxSuccessful());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(inventory().get("count").asInt()).isZero();
            assertThat(scene().get("wornCount").asInt()).isZero();
        });
    }

    @Test
    void 탐험가_식별_없으면_401() throws Exception {
        assertThat(mvc.perform(get("/inventory")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/scene")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void 운영_아이템_추가는_관리자_토큰으로_보호하고_입력을_검증한다() throws Exception {
        String itemId = "event:api-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("itemId", itemId);
        form.put("name", "세종 별 핀");
        form.put("emoji", "📍");
        form.put("slot", "BADGE");
        form.put("tier", "RARE");
        form.put("look", Map.of("type", "star", "primary", "#f4c542", "secondary", "#2b3542"));
        form.put("grantRule", "PROVINCE_CHECK_IN");
        form.put("grantRef", "KR-29"); // 세종(지역 1곳) — 같은 DB 를 쓰는 다른 테스트의 체크인이 받지 않게

        error(send(post("/admin/items"), form), 401, "ADMIN_TOKEN_REQUIRED");
        error(send(post("/admin/items").header(ADMIN, "wrong"), form), 403, "ADMIN_TOKEN_INVALID");

        JsonNode created = json(send(post("/admin/items").header(ADMIN, ADMIN_TOKEN), form).andExpect(status().isCreated()));
        assertThat(created.get("itemId").asText()).isEqualTo(itemId);
        assertThat(created.get("grantRule").asText()).isEqualTo("PROVINCE_CHECK_IN");
        mvc.perform(get("/catalog/items")).andExpect(status().isOk()).andExpect(jsonPath("$[*].itemId", hasItem(itemId)));

        error(send(post("/admin/items").header(ADMIN, ADMIN_TOKEN), form), 409, "ITEM_ALREADY_EXISTS");
        Map<String, Object> unknownProvince = new LinkedHashMap<>(form);
        unknownProvince.put("itemId", itemId + "x");
        unknownProvince.put("grantRef", "KR-99");
        error(send(post("/admin/items").header(ADMIN, ADMIN_TOKEN), unknownProvince), 400, "UNKNOWN_ITEM_REFERENCE");
        Map<String, Object> noPeriod = new LinkedHashMap<>(form);
        noPeriod.put("itemId", itemId + "y");
        noPeriod.put("grantRule", "PERIOD_CHECK_IN");
        error(send(post("/admin/items").header(ADMIN, ADMIN_TOKEN), noPeriod), 400, "INVALID_ITEM_DEFINITION");
        Map<String, Object> badColor = new LinkedHashMap<>(form);
        badColor.put("itemId", itemId + "z");
        badColor.put("look", Map.of("type", "star", "primary", "yellow", "secondary", "#2b3542"));
        error(send(post("/admin/items").header(ADMIN, ADMIN_TOKEN), badColor), 400, "INVALID_ITEM_DEFINITION");
        Map<String, Object> missing = new LinkedHashMap<>(form);
        missing.remove("slot");
        error(send(post("/admin/items").header(ADMIN, ADMIN_TOKEN), missing), 400, "VALIDATION_FAILED");

        // 시·도 이슈 아이템: 세종 체크인으로 받는다
        checkIn("KR-29010");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(inventory().get("items").findValuesAsText("itemId"))
            .contains(itemId, "region:KR-29010"));
    }

    @Test
    void 카탈로그_아이템은_DB_기준_지급_규칙을_보여준다() throws Exception {
        JsonNode items = json(mvc.perform(get("/catalog/items")).andExpect(status().isOk()));
        assertThat(items.size()).isGreaterThanOrEqualTo(259);
        JsonNode first = items.get(0);
        assertThat(first.has("grantRule")).isTrue();
        assertThat(items.findValuesAsText("itemId")).contains("region:KR-11010", "set:jiri");
    }

    @Test
    void 사용자_장면_편집과_자동_착용이_겹쳐도_실패가_남지_않고_착용은_가방과_맞는다_P3_17() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<List<Integer>> edits = pool.submit(() -> {
                List<Integer> statuses = new ArrayList<>();
                for (int i = 0; i < 30; i++) {
                    statuses.add(send(as(put("/scene")), Map.of("gender", i % 2 == 0 ? "F" : "M")).andReturn().getResponse().getStatus());
                }
                return statuses;
            });
            for (String code : List.of("KR-11010", "KR-36330", "KR-21090", "KR-38370", "KR-11020")) checkIn(code);
            assertThat(edits.get(30, TimeUnit.SECONDS)).allMatch(status -> status == 200 || status == 409);
        } finally {
            pool.shutdownNow();
        }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(inventory().get("count").asInt()).isEqualTo(5);
            assertThat(scene().get("wornCount").asInt()).isEqualTo(5); // 빈 슬롯·빈 장식 칸 → 전부 자동 착용
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery WHERE subscriber LIKE 'wardrobe.%' AND status = 'FAILED'",
            Integer.class)).isZero();
    }
}
