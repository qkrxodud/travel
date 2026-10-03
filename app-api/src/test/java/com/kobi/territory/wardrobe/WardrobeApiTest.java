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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 3단계 D3: 꾸미기 API 계약(MockMvc) — 가방, 즐겨찾기, 장면 보기·편집, 운영 아이템 추가, 아이템 안내(DB 기준).
 * 회귀 출처: QA P3-17(사용자 편집과 자동 착용 경합).
 */
@IntegrationTest
@DisplayName("가방과 장면 꾸미기")
class WardrobeApiTest {

    static final String ADMIN = "X-Admin-Token";
    static final String ADMIN_TOKEN = "local-admin-token";
    static final Duration WAIT = Duration.ofSeconds(15);

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

    private void 칠한다(String code) throws Exception {
        send(as(post("/visits")), Map.of("regionCode", code, "visitDate", LocalDate.now(clock).toString()))
            .andExpect(status().isCreated());
    }

    private JsonNode 가방() throws Exception {
        return json(mvc.perform(as(get("/inventory"))).andExpect(status().isOk()));
    }

    private JsonNode 장면() throws Exception {
        return json(mvc.perform(as(get("/scene"))).andExpect(status().isOk()));
    }

    private ResultActions 장면을_고친다(Object edit) throws Exception {
        return send(as(put("/scene")), edit);
    }

    private static void error(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("처음엔 가방이 비어 있고 장면은 기본 성별에 아무것도 입지 않았다")
    void emptyAtStart() throws Exception {
        assertThat(가방().get("count").asInt()).isZero();
        JsonNode scene = 장면();
        assertThat(scene.get("gender").asText()).isEqualTo("M");
        assertThat(scene.get("stylePoints").asInt()).isZero();
        assertThat(scene.get("slots").has("HAT")).isTrue();
        assertThat(scene.get("slots").get("HAT").isNull()).isTrue();
        assertThat(scene.get("props")).isEmpty();
    }

    @Test
    @DisplayName("누구인지 모르면 가방도 장면도 볼 수 없다")
    void needsExplorer() throws Exception {
        assertThat(mvc.perform(get("/inventory")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/scene")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Nested
    @DisplayName("지역을 칠하면")
    class CheckIn {

        /** 종로(청사초롱 등불 HAND 일반 1점) + 구례(산수유 비니 HAT 희귀 3점). */
        private void 종로와_구례를_칠한다() throws Exception {
            칠한다("KR-11010");
            칠한다("KR-36330");
            await().atMost(WAIT).untilAsserted(() -> assertThat(장면().get("wornCount").asInt()).isEqualTo(2));
        }

        @Test
        @DisplayName("그 지역 아이템이 가방에 들어오고 빈 슬롯에 자동으로 입혀진다")
        void itemGrantedAndWorn() throws Exception {
            종로와_구례를_칠한다();
            JsonNode inventory = 가방();
            assertThat(inventory.get("count").asInt()).isEqualTo(2);
            JsonNode first = inventory.get("items").get(0);
            assertThat(first.get("itemId").asText()).isIn("region:KR-11010", "region:KR-36330");
            assertThat(first.get("name").asText()).isNotBlank();
            assertThat(first.get("source").asText()).isEqualTo("REGION");
            assertThat(first.get("equipped").asBoolean()).isTrue();
            JsonNode scene = 장면();
            assertThat(scene.get("slots").get("HAND").get("itemId").asText()).isEqualTo("region:KR-11010");
            assertThat(scene.get("slots").get("HAT").get("tier").asText()).isEqualTo("RARE");
        }

        @Test
        @DisplayName("꾸미기 점수는 입은 아이템 희귀도 점수의 합으로 서버가 계산한다")
        void stylePointsAreSumOfRarity() throws Exception {
            종로와_구례를_칠한다();
            await().atMost(WAIT).untilAsserted(() -> assertThat(장면().get("stylePoints").asInt()).isEqualTo(4));
        }

        @Test
        @DisplayName("취소하면 그 아이템이 가방에서 빠지고 벗겨진다")
        void cancelRemovesAndUnequips() throws Exception {
            칠한다("KR-11010");
            await().atMost(WAIT).untilAsserted(() -> assertThat(장면().get("wornCount").asInt()).isEqualTo(1));
            mvc.perform(as(delete("/visits/KR-11010"))).andExpect(status().is2xxSuccessful());
            await().atMost(WAIT).untilAsserted(() -> {
                assertThat(가방().get("count").asInt()).isZero();
                assertThat(장면().get("wornCount").asInt()).isZero();
            });
        }

        @Test
        @DisplayName("직접 장면을 고치는 사이 자동 착용이 겹쳐도 실패가 남지 않고 입은 것은 가방과 맞는다")
        void userEditAndAutoEquipOverlap() throws Exception {
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<List<Integer>> edits = pool.submit(() -> {
                    List<Integer> statuses = new ArrayList<>();
                    for (int i = 0; i < 30; i++) {
                        statuses.add(장면을_고친다(Map.of("gender", i % 2 == 0 ? "F" : "M")).andReturn().getResponse().getStatus());
                    }
                    return statuses;
                });
                for (String code : List.of("KR-11010", "KR-36330", "KR-21090", "KR-38370", "KR-11020")) 칠한다(code);
                assertThat(edits.get(30, TimeUnit.SECONDS)).allMatch(status -> status == 200 || status == 409);
            } finally {
                pool.shutdownNow();
            }
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                assertThat(가방().get("count").asInt()).isEqualTo(5);
                assertThat(장면().get("wornCount").asInt()).isEqualTo(5);
            });
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery WHERE subscriber LIKE 'wardrobe.%' AND status = 'FAILED'",
                Integer.class)).isZero();
        }
    }

    @Nested
    @DisplayName("장면을 직접 고칠 때")
    class SceneEdit {

        /** 종로(청사초롱 등불 HAND) + 해운대(파라솔 장식)를 칠해 둘 다 입은 상태. */
        @BeforeEach
        void 종로와_해운대를_입는다() throws Exception {
            칠한다("KR-11010");
            칠한다("KR-21090");
            await().atMost(WAIT).untilAsserted(() -> assertThat(가방().get("count").asInt()).isEqualTo(2));
            await().atMost(WAIT).untilAsserted(() -> assertThat(장면().get("props")).hasSize(1));
        }

        @Test
        @DisplayName("성별을 바꾸고 손 아이템을 벗고 장식을 비우면 꾸미기 점수가 0이 된다")
        void changeGenderUnequipAndClearProps() throws Exception {
            Map<String, Object> edit = new LinkedHashMap<>();
            edit.put("gender", "F");
            edit.put("unequip", List.of("HAND"));
            edit.put("props", List.of());
            JsonNode changed = json(장면을_고친다(edit).andExpect(status().isOk()));
            assertThat(changed.get("gender").asText()).isEqualTo("F");
            assertThat(changed.get("slots").get("HAND").isNull()).isTrue();
            assertThat(changed.get("props")).isEmpty();
            assertThat(changed.get("stylePoints").asInt()).isZero();
        }

        @Test
        @DisplayName("가방에 있는 아이템을 맞는 슬롯과 장식 칸에 입히고, 말하지 않은 성별은 그대로다")
        void equipOwnedItems() throws Exception {
            장면을_고친다(Map.of("gender", "F", "unequip", List.of("HAND"), "props", List.of())).andExpect(status().isOk());
            JsonNode equipped = json(장면을_고친다(Map.of("equip", Map.of("HAND", "region:KR-11010"),
                "props", List.of("region:KR-21090"))).andExpect(status().isOk()));
            assertThat(equipped.get("slots").get("HAND").get("name").asText()).isEqualTo("청사초롱 등불");
            assertThat(equipped.get("props").get(0).get("itemId").asText()).isEqualTo("region:KR-21090");
            assertThat(equipped.get("gender").asText()).isEqualTo("F");
        }

        @Test
        @DisplayName("아이템 슬롯과 다른 슬롯에는 입힐 수 없다")
        void slotMismatch() throws Exception {
            error(장면을_고친다(Map.of("equip", Map.of("HAT", "region:KR-11010"))), 422, "SLOT_MISMATCH");
        }

        @Test
        @DisplayName("가방에 없는 아이템은 입힐 수 없다")
        void notOwned() throws Exception {
            error(장면을_고친다(Map.of("equip", Map.of("HAND", "region:KR-11030"))), 422, "ITEM_NOT_OWNED");
        }

        @Test
        @DisplayName("없는 아이템은 입힐 수 없다")
        void unknownItem() throws Exception {
            error(장면을_고친다(Map.of("equip", Map.of("HAND", "region:KR-99999"))), 404, "ITEM_NOT_FOUND");
        }

        @Test
        @DisplayName("같은 장식을 두 칸에 놓을 수 없다")
        void duplicateProp() throws Exception {
            error(장면을_고친다(Map.of("props", List.of("region:KR-21090", "region:KR-21090"))), 400, "DUPLICATE_PROP");
        }

        @Test
        @DisplayName("없는 슬롯 이름은 읽을 수 없는 요청이다")
        void unknownSlot() throws Exception {
            error(장면을_고친다(Map.of("equip", Map.of("WING", "region:KR-11010"))), 400, "MALFORMED_REQUEST");
        }

        @Test
        @DisplayName("없는 성별 값은 읽을 수 없는 요청이다")
        void unknownGender() throws Exception {
            error(장면을_고친다(Map.of("gender", "X")), 400, "MALFORMED_REQUEST");
        }
    }

    @Nested
    @DisplayName("즐겨찾기")
    class Favorite {

        @BeforeEach
        void 종로_아이템을_받는다() throws Exception {
            칠한다("KR-11010");
            await().atMost(WAIT).untilAsserted(() -> assertThat(가방().get("count").asInt()).isEqualTo(1));
        }

        @Test
        @DisplayName("표시하면 가방에 즐겨찾기로 남는다")
        void markFavorite() throws Exception {
            JsonNode item = json(send(as(put("/inventory/region:KR-11010/favorite")), Map.of("favorite", true))
                .andExpect(status().isOk()));
            assertThat(item.get("favorite").asBoolean()).isTrue();
            assertThat(가방().get("items").get(0).get("favorite").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("가방에 없는 아이템은 표시할 수 없다")
        void notOwned() throws Exception {
            error(send(as(put("/inventory/region:KR-26010/favorite")), Map.of("favorite", true)), 422, "ITEM_NOT_OWNED");
        }

        @Test
        @DisplayName("표시 값을 빼먹으면 입력을 확인하라고 한다")
        void missingValue() throws Exception {
            error(send(as(put("/inventory/region:KR-11010/favorite")), Map.of()), 400, "VALIDATION_FAILED");
        }
    }

    @Nested
    @DisplayName("운영자가 이슈 아이템을 추가할 때")
    class AdminItems {

        private String itemId;
        private Map<String, Object> form;

        @BeforeEach
        void 세종_별_핀_양식() {
            itemId = "event:api-" + UUID.randomUUID().toString().substring(0, 8);
            form = new LinkedHashMap<>();
            form.put("itemId", itemId);
            form.put("name", "세종 별 핀");
            form.put("emoji", "📍");
            form.put("slot", "BADGE");
            form.put("tier", "RARE");
            form.put("look", Map.of("type", "star", "primary", "#f4c542", "secondary", "#2b3542"));
            form.put("grantRule", "PROVINCE_CHECK_IN");
            form.put("grantRef", "KR-29"); // 세종(지역 1곳) — 같은 DB 를 쓰는 다른 테스트의 체크인이 받지 않게
        }

        private ResultActions 추가(Map<String, Object> body) throws Exception {
            return send(post("/admin/items").header(ADMIN, ADMIN_TOKEN), body);
        }

        private Map<String, Object> 바꾼_양식(String suffix, String key, Object value) {
            Map<String, Object> changed = new LinkedHashMap<>(form);
            changed.put("itemId", itemId + suffix);
            if (value == null) changed.remove(key);
            else changed.put(key, value);
            return changed;
        }

        @Test
        @DisplayName("관리자 토큰으로 추가하면 아이템 안내에 바로 보인다")
        void addedItemIsListed() throws Exception {
            JsonNode created = json(추가(form).andExpect(status().isCreated()));
            assertThat(created.get("itemId").asText()).isEqualTo(itemId);
            assertThat(created.get("grantRule").asText()).isEqualTo("PROVINCE_CHECK_IN");
            mvc.perform(get("/catalog/items")).andExpect(status().isOk()).andExpect(jsonPath("$[*].itemId", hasItem(itemId)));
        }

        @Test
        @DisplayName("추가한 시·도 이슈 아이템은 그 시·도를 칠하면 받는다")
        void grantedByProvinceCheckIn() throws Exception {
            추가(form).andExpect(status().isCreated());
            칠한다("KR-29010");
            await().atMost(WAIT).untilAsserted(() -> assertThat(가방().get("items").findValuesAsText("itemId"))
                .contains(itemId, "region:KR-29010"));
        }

        @Test
        @DisplayName("관리자 토큰이 없으면 추가할 수 없다")
        void tokenRequired() throws Exception {
            error(send(post("/admin/items"), form), 401, "ADMIN_TOKEN_REQUIRED");
        }

        @Test
        @DisplayName("틀린 관리자 토큰으로는 추가할 수 없다")
        void wrongToken() throws Exception {
            error(send(post("/admin/items").header(ADMIN, "wrong"), form), 403, "ADMIN_TOKEN_INVALID");
        }

        @Test
        @DisplayName("같은 아이템은 다시 추가할 수 없다")
        void duplicate() throws Exception {
            추가(form).andExpect(status().isCreated());
            error(추가(form), 409, "ITEM_ALREADY_EXISTS");
        }

        @Test
        @DisplayName("없는 시·도를 가리키면 거절된다")
        void unknownProvince() throws Exception {
            error(추가(바꾼_양식("x", "grantRef", "KR-99")), 400, "UNKNOWN_ITEM_REFERENCE");
        }

        @Test
        @DisplayName("기간 이슈 아이템은 기간이 있어야 한다")
        void periodRequired() throws Exception {
            error(추가(바꾼_양식("y", "grantRule", "PERIOD_CHECK_IN")), 400, "INVALID_ITEM_DEFINITION");
        }

        @Test
        @DisplayName("색은 정해진 형식으로만 적는다")
        void colorFormat() throws Exception {
            error(추가(바꾼_양식("z", "look", Map.of("type", "star", "primary", "yellow", "secondary", "#2b3542"))), 400,
                "INVALID_ITEM_DEFINITION");
        }

        @Test
        @DisplayName("슬롯 같은 필수 항목이 빠지면 입력을 확인하라고 한다")
        void requiredField() throws Exception {
            error(추가(바꾼_양식("w", "slot", null)), 400, "VALIDATION_FAILED");
        }
    }

    @Test
    @DisplayName("아이템 안내는 운영이 등록한 정의 기준으로 지역 특산물과 테마 배경을 지급 규칙과 함께 보여 준다")
    void catalogItemsFromDatabase() throws Exception {
        JsonNode items = json(mvc.perform(get("/catalog/items")).andExpect(status().isOk()));
        assertThat(items.size()).isGreaterThanOrEqualTo(259);
        assertThat(items.get(0).has("grantRule")).isTrue();
        assertThat(items.findValuesAsText("itemId")).contains("region:KR-11010", "set:jiri");
    }
}
