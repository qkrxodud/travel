package com.kobi.territory.exploration;

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
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.application.MapPurgeJob;
import com.kobi.territory.outbox.OutboxRedelivery;
import com.kobi.territory.support.IntegrationTestConfig.FaultInjection;
import com.kobi.territory.progression.application.RecalculateService;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 3단계 공유 지도 D2·D3: 토큰 인증 → 지도 만들기·초대코드 합류 → 각자 체크인·선점 → 지도장 설정·이의 → 테마 완성 멤버 전원 보상 →
 * 탈퇴(숨김·선점 이전 +10) → 재가입 복구(선점은 안 돌아옴) → 유예 종료 하드 삭제, 재계산 R2-1 회귀.
 */
@IntegrationTest
class SharedMapIntegrationTest {

    private static final String H = "X-Explorer-Token";
    static final List<String> JIRI = List.of("KR-35050", "KR-36330", "KR-38360", "KR-38370", "KR-38380");
    static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired MapPurgeJob purgeJob;
    @Autowired RecalculateService recalculate;
    @Autowired OutboxRedelivery redelivery;

    record Who(String id, String token) {}

    private Who register() throws Exception {
        JsonNode body = json(mvc.perform(post("/explorers")).andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty()));
        return new Who(body.get("explorerId").asText(), body.get("accessToken").asText());
    }

    private JsonNode json(ResultActions result) throws Exception {
        return om.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions call(Who who, MockHttpServletRequestBuilder builder, Object body) throws Exception {
        builder.header(H, who.token());
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        return mvc.perform(builder);
    }

    private ResultActions checkIn(Who who, String mapId, String code) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("regionCode", code, "visitDate", LocalDate.now(clock).toString()));
        if (mapId != null) body.put("mapId", mapId);
        clock.advance(Duration.ofSeconds(1)); // 실제처럼 체크인마다 처리 시각이 다르게(선점 순서)
        return call(who, post("/visits"), body);
    }

    private static void error(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    private long xp(Who who) {
        Long xp = jdbc.query("SELECT xp FROM explorer_progress WHERE explorer_id = ?",
            resultSet -> resultSet.next() ? resultSet.getLong(1) : null, who.id());
        return xp == null ? 0 : xp;
    }

    private boolean ledgerHas(Who who, String refId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND ref_id = ?", Integer.class,
            who.id(), refId) == 1;
    }

    private void awaitRelayed(String... aggregateIds) {
        await().atMost(WAIT).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE published_at IS NULL "
            + "AND aggregate_id IN (" + String.join(",", java.util.Collections.nCopies(aggregateIds.length, "?")) + ")",
            Integer.class, (Object[]) aggregateIds) == 0);
    }

    private String createMap(Who owner, String name) throws Exception {
        JsonNode created = json(call(owner, post("/maps"), Map.of("name", name)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("SHARED"))
            .andExpect(jsonPath("$.rules.length()").value(3))
            .andExpect(jsonPath("$.members[0].role").value("OWNER")));
        return created.get("mapId").asText();
    }

    private String inviteCodeOf(Who member, String mapId) throws Exception {
        return json(call(member, get("/maps/" + mapId), null)).get("inviteCode").asText();
    }

    @Test
    void 토큰_인증과_공유_지도_합류_선점_설정_이의() throws Exception {
        Who owner = register();
        Who friend = register();
        Who stranger = register();
        String mapId = createMap(owner, "부산 원정대");

        // N2: 비멤버는 잠금 없이 바로 403
        error(checkIn(friend, mapId, "KR-26010"), 403, "NOT_A_MEMBER");
        error(call(friend, post("/maps/join"), Map.of("inviteCode", "ZZZZZZZZ")), 404, "INVITE_CODE_NOT_FOUND");
        String code = inviteCodeOf(owner, mapId);
        call(friend, post("/maps/join"), Map.of("inviteCode", code.toLowerCase())).andExpect(status().isOk())
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.rejoined").value(false));
        error(call(friend, post("/maps/join"), Map.of("inviteCode", code)), 409, "ALREADY_MEMBER");
        call(friend, get("/maps"), null).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].kind").value("PERSONAL")).andExpect(jsonPath("$[1].mapId").value(mapId));

        // 각자 체크인 — 지역 색은 선점자(먼저 칠한 사람)
        checkIn(owner, mapId, "KR-26010").andExpect(status().isCreated()).andExpect(jsonPath("$.firstClaim").value(true));
        checkIn(friend, mapId, "KR-26010").andExpect(status().isCreated()).andExpect(jsonPath("$.firstClaim").value(false));
        checkIn(friend, mapId, "KR-26020").andExpect(status().isCreated());
        JsonNode detail = json(call(friend, get("/maps/" + mapId), null));
        assertThat(detail.get("claims").toString()).contains("KR-26010\",\"explorerId\":\"" + owner.id());
        assertThat(detail.get("members").get(0).get("color").asText()).isNotEqualTo(detail.get("members").get(1).get("color").asText());
        assertThat(detail.get("members").get(1).get("regionCount").asInt()).isEqualTo(2);
        // 다른 멤버 방문의 메모는 비공개
        call(owner, get("/territory").param("mapId", mapId), null).andExpect(jsonPath("$.visits.length()").value(3))
            .andExpect(jsonPath("$.claims.length()").value(2));
        error(call(stranger, get("/maps/" + mapId), null), 403, "NOT_A_MEMBER");

        // 지도장 설정: 사진 필수 · 하루 상한
        error(call(friend, put("/maps/" + mapId + "/settings"), Map.of("photoRequired", true, "dailyCheckInCap", 1)), 403,
            "OWNER_ONLY");
        call(owner, put("/maps/" + mapId + "/settings"), Map.of("photoRequired", true, "dailyCheckInCap", 1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.settings.photoRequired").value(true));
        error(checkIn(friend, mapId, "KR-26030"), 422, "PHOTO_REQUIRED");
        call(friend, post("/dev/explorers/age"), Map.of("hours", 73)).andExpect(status().isOk());
        call(friend, post("/visits"), Map.of("regionCode", "KR-26030", "visitDate", LocalDate.now(clock).toString(),
            "mapId", mapId, "photoUrl", "https://example.com/a.jpg")).andExpect(status().is(422))
            .andExpect(jsonPath("$.code").value("DAILY_CAP_EXCEEDED")); // 오늘 이미 2곳 > 상한 1
        call(owner, put("/maps/" + mapId + "/settings"), Map.of("photoRequired", false, "dailyCheckInCap", 5))
            .andExpect(status().isOk());
        // 하루 상한은 기본값(5) 이하로만(Q1), 개인 지도는 설정 불가
        error(call(owner, put("/maps/" + mapId + "/settings"), Map.of("photoRequired", false, "dailyCheckInCap", 6)), 400,
            "INVALID_SETTINGS");
        String ownerPersonal = json(call(owner, get("/explorers/me"), null)).get("personalMapId").asText();
        error(call(owner, put("/maps/" + ownerPersonal + "/settings"), Map.of("photoRequired", false, "dailyCheckInCap", 3)),
            422, "PERSONAL_MAP_ONLY_ME");

        // 지도장 이의
        String disputePath = "/maps/" + mapId + "/visits/KR-26020/" + friend.id() + "/dispute";
        error(call(friend, put(disputePath), Map.of("disputed", true)), 403, "OWNER_ONLY");
        call(owner, put(disputePath), Map.of("disputed", true)).andExpect(status().isOk())
            .andExpect(jsonPath("$.disputed").value(true));
        call(owner, get("/maps/" + mapId), null).andExpect(jsonPath("$.disputed[0].regionCode").value("KR-26020"));

        // 초대코드 재발급(지도장만) → 예전 코드는 무효
        error(call(friend, post("/maps/" + mapId + "/invite-code"), null), 403, "OWNER_ONLY");
        String newCode = json(call(owner, post("/maps/" + mapId + "/invite-code"), null)).get("inviteCode").asText();
        assertThat(newCode).isNotEqualTo(code);
        error(call(stranger, post("/maps/join"), Map.of("inviteCode", code)), 404, "INVITE_CODE_NOT_FOUND");

        // 지도장은 넘기기 전에는 탈퇴 불가
        error(call(owner, post("/maps/" + mapId + "/leave"), null), 422, "OWNER_CANNOT_LEAVE");
        call(owner, post("/maps/" + mapId + "/transfer-owner"), Map.of("explorerId", friend.id())).andExpect(status().isOk())
            .andExpect(jsonPath("$.ownerId").value(friend.id()));
        // 개인 지도는 탈퇴할 수 없다
        String personal = json(call(owner, get("/explorers/me"), null)).get("personalMapId").asText();
        error(call(owner, post("/maps/" + personal + "/leave"), null), 422, "PERSONAL_MAP_ONLY_ME");
    }

    @Test
    void 테마_완성은_완성_시점_멤버_전원이_받고_나중_합류는_받지_않는다_재계산도_같다_R2_1() throws Exception {
        Who owner = register();
        Who friend = register();
        Who late = register();
        String mapId = createMap(owner, "지리산 원정");
        call(friend, post("/maps/join"), Map.of("inviteCode", inviteCodeOf(owner, mapId))).andExpect(status().isOk());

        // 친구는 한 곳도 칠하지 않았지만 완성 시점 멤버라 수령자
        for (String code : JIRI) checkIn(owner, mapId, code).andExpect(status().isCreated());
        awaitRelayed(mapId, owner.id(), friend.id());
        await().atMost(WAIT).until(() -> ledgerHas(friend, "set:" + friend.id() + ":jiri"));
        assertThat(ledgerHas(owner, "set:" + owner.id() + ":jiri")).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM title_earned WHERE explorer_id = ? AND title_id = 'set-jiri'",
            Integer.class, friend.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT completed_member_ids FROM set_progress WHERE map_id = ? AND set_id = 'jiri'",
            String.class, mapId)).contains(owner.id(), friend.id());

        // 나중 합류 → XP·칭호 없음(재계산해도 — R2-1)
        call(late, post("/maps/join"), Map.of("inviteCode", inviteCodeOf(owner, mapId))).andExpect(status().isOk());
        awaitRelayed(mapId, late.id());
        recalculate.recalculate(ExplorerId.of(late.id()));
        recalculate.recalculate(ExplorerId.of(friend.id()));
        assertThat(ledgerHas(late, "set:" + late.id() + ":jiri")).isFalse();
        assertThat(ledgerHas(friend, "set:" + friend.id() + ":jiri")).isTrue();
        call(late, get("/collection").param("mapId", mapId), null).andExpect(status().isOk());
    }

    @Test
    void 탈퇴는_방문을_숨기고_선점을_넘기며_재가입하면_복구되고_유예가_끝나면_지운다() throws Exception {
        Who owner = register();
        Who leaver = register();
        String mapId = createMap(owner, "강원 원정");
        call(leaver, post("/maps/join"), Map.of("inviteCode", inviteCodeOf(owner, mapId))).andExpect(status().isOk());
        checkIn(leaver, mapId, "KR-32010").andExpect(status().isCreated()); // leaver 선점
        checkIn(owner, mapId, "KR-32010").andExpect(status().isCreated());
        checkIn(leaver, mapId, "KR-32020").andExpect(status().isCreated()); // leaver 혼자
        awaitRelayed(mapId, owner.id(), leaver.id());
        long ownerXp = xp(owner);
        long leaverXp = xp(leaver);

        JsonNode left = json(call(leaver, post("/maps/" + mapId + "/leave"), null).andExpect(status().isOk()));
        assertThat(left.get("hiddenRegionCount").asInt()).isEqualTo(2);
        assertThat(left.get("message").asText()).contains("2곳");
        error(checkIn(leaver, mapId, "KR-32030"), 403, "NOT_A_MEMBER");
        awaitRelayed(mapId, owner.id(), leaver.id());

        // 숨김 + 선점 이전(+10 → 새 선점자), 탐험가 단위 기록은 줄지 않는다
        call(owner, get("/territory").param("mapId", mapId), null).andExpect(jsonPath("$.visits.length()").value(1));
        call(owner, get("/maps/" + mapId), null).andExpect(jsonPath("$.claims[0].explorerId").value(owner.id()))
            .andExpect(jsonPath("$.departing").value(1));
        await().atMost(WAIT).until(() -> xp(owner) == ownerXp + 10);
        assertThat(ledgerHas(owner, "claim:" + mapId + ":KR-32010:" + owner.id())).isTrue();
        assertThat(xp(leaver)).isEqualTo(leaverXp); // 떠난 사람 보너스·기본 XP 회수 없음
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM explorer_region WHERE explorer_id = ? AND active_map_count > 0",
            Integer.class, leaver.id())).isEqualTo(2);

        // 유예 안 재가입 → 복구(선점은 안 돌아옴)
        clock.advance(Duration.ofMinutes(1));
        call(leaver, post("/maps/join"), Map.of("inviteCode", inviteCodeOf(owner, mapId))).andExpect(status().isOk())
            .andExpect(jsonPath("$.rejoined").value(true));
        awaitRelayed(mapId);
        call(leaver, get("/territory").param("mapId", mapId), null).andExpect(jsonPath("$.visits.length()").value(3));
        call(leaver, get("/maps/" + mapId), null).andExpect(status().isOk());
        JsonNode claims = json(call(leaver, get("/maps/" + mapId), null)).get("claims");
        assertThat(claims.toString()).contains("KR-32010\",\"explorerId\":\"" + owner.id())
            .contains("KR-32020\",\"explorerId\":\"" + leaver.id());

        // 다시 탈퇴 → 유예(7일) 지나면 배치가 하드 삭제
        call(leaver, post("/maps/" + mapId + "/leave"), null).andExpect(status().isOk());
        awaitRelayed(mapId);
        assertThat(purgeJob.run()).isZero(); // 아직 유예 중
        clock.advance(Duration.ofDays(8));
        assertThat(purgeJob.run()).isEqualTo(1);
        awaitRelayed(mapId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE map_id = ? AND checked_in_by = ?", Integer.class,
            mapId, leaver.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM map_member WHERE map_id = ? AND explorer_id = ?", Integer.class,
            mapId, leaver.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM explorer_region WHERE explorer_id = ? AND active_map_count > 0",
            Integer.class, leaver.id())).isEqualTo(2); // §5 전체 랭킹은 줄지 않는다
        // 재계산해도 같다(탈퇴한 지도의 활성은 얼어 있다)
        recalculate.recalculate(ExplorerId.of(leaver.id()));
        assertThat(xp(leaver)).isEqualTo(leaverXp);
    }

    @Test
    void 체크인_회차는_취소_후_다시_칠하면_오르고_이벤트에_실린다() throws Exception {
        Who me = register();
        checkIn(me, null, "KR-11010").andExpect(status().isCreated()).andExpect(jsonPath("$.visit.generation").value(1));
        call(me, delete("/visits/KR-11010"), null).andExpect(status().isNoContent());
        checkIn(me, null, "KR-11010").andExpect(status().isCreated()).andExpect(jsonPath("$.visit.generation").value(2));
        List<String> payloads = jdbc.queryForList("SELECT payload FROM outbox WHERE event_type LIKE '%RegionVisited' "
            + "AND payload LIKE ? ORDER BY id", String.class, "%" + me.id() + "%");
        assertThat(payloads).hasSize(2);
        assertThat(om.readTree(payloads.get(1)).get("visitGeneration").asInt()).isEqualTo(2);
        assertThat(om.readTree(payloads.get(1)).get("memberIds").get(0).asText()).isEqualTo(me.id());
        String cancelled = jdbc.queryForObject("SELECT payload FROM outbox WHERE event_type LIKE '%VisitCancelled' AND payload LIKE ?",
            String.class, "%" + me.id() + "%");
        assertThat(om.readTree(cancelled).get("visitGeneration").asInt()).isEqualTo(1);

        // 방문 행이 물리 삭제돼도 회차는 단조 증가한다(visit_generation) — 체크인→취소→재체크인→취소→재체크인
        call(me, delete("/visits/KR-11010"), null).andExpect(status().isNoContent());
        checkIn(me, null, "KR-11010").andExpect(status().isCreated()).andExpect(jsonPath("$.visit.generation").value(3));
        List<Integer> generations = jdbc.queryForList("SELECT payload FROM outbox WHERE payload LIKE ? AND "
            + "(event_type LIKE '%RegionVisited' OR event_type LIKE '%VisitCancelled') ORDER BY id", String.class, "%" + me.id() + "%")
            .stream().map(payload -> { try { return om.readTree(payload).get("visitGeneration").asInt(); }
                catch (Exception exception) { throw new IllegalStateException(exception); } }).toList();
        assertThat(generations).containsExactly(1, 1, 2, 2, 3); // 체크인1·취소1·체크인2·취소2·체크인3
        // 진행·꾸미기가 마지막 회차를 활성으로 본다(XP 회수 후 다시 지급 = 기본 10 활성 1개)
        await().atMost(WAIT).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? "
            + "AND ref_id LIKE 'region:%'", Integer.class, me.id()) == 5);
        assertThat(ledgerHas(me, "region:" + me.id() + ":KR-11010#3")).isTrue();
        assertThat(jdbc.queryForObject("SELECT active_map_count FROM explorer_region WHERE explorer_id = ? AND region_code = 'KR-11010'",
            Integer.class, me.id())).isEqualTo(1);
    }

    @Test
    void 가입하면_진행_루트_행을_미리_만든다_S3_1() throws Exception {
        Who me = register();
        await().atMost(WAIT).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM explorer_progress WHERE explorer_id = ?",
            Integer.class, me.id()) == 1);
        assertThat(xp(me)).isZero();
    }

    @Test
    void 미전달_이벤트가_남은_탐험가는_재계산을_보류한다_S3_3() throws Exception {
        Who me = register();
        String personal = json(call(me, get("/explorers/me"), null)).get("personalMapId").asText();
        awaitRelayed(personal, me.id());
        FaultInjection.failNext("progression.progress", RegionVisited.class, me.id(), 5); // 5회 실패 → FAILED(멈춤)
        try {
            checkIn(me, null, "KR-11010").andExpect(status().isCreated());
            await().atMost(WAIT).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery d JOIN outbox o "
                + "ON o.id = d.event_id WHERE o.aggregate_id = ? AND d.status = 'FAILED'", Integer.class, personal) == 1);
            assertThat(recalculate.recalculateIfSettled(ExplorerId.of(me.id()))).isFalse();
            assertThat(recalculate.recalculateAll().deferredExplorerIds()).contains(me.id());
        } finally {
            FaultInjection.clear();
        }
        redelivery.redeliverFailed(null, "progression.progress");
        awaitRelayed(personal, me.id());
        assertThat(recalculate.recalculateIfSettled(ExplorerId.of(me.id()))).isTrue();
        assertThat(xp(me)).isEqualTo(35);
    }

    @Test
    void 필드가_없던_예전_payload도_역직렬화된다_회차_0_멤버_null() throws Exception {
        String visited = "{\"explorerId\":\"11111111-1111-1111-1111-111111111111\",\"mapId\":\"33333333-3333-3333-3333-333333333333\","
            + "\"regionCode\":\"KR-11010\",\"rarity\":\"COMMON\",\"provinceCode\":\"KR-11\",\"visitedAt\":\"2026-10-02T03:00:00Z\","
            + "\"visitDate\":\"2026-10-02\",\"isFirstInProvince\":true,\"nth\":1,\"isFirstClaim\":true}";
        var regionVisited = om.readValue(visited, RegionVisited.class);
        assertThat(regionVisited.visitGeneration()).isZero();
        assertThat(regionVisited.memberIds()).isNull();
        String cancelled = "{\"explorerId\":\"11111111-1111-1111-1111-111111111111\",\"mapId\":\"33333333-3333-3333-3333-333333333333\","
            + "\"regionCode\":\"KR-11010\",\"rarity\":\"COMMON\",\"provinceCode\":\"KR-11\",\"wasClaim\":true,\"remaining\":0,"
            + "\"regionStillOnMap\":false,\"cancelledAt\":\"2026-10-02T03:00:01Z\"}";
        assertThat(om.readValue(cancelled, com.kobi.territory.exploration.api.event.VisitCancelled.class).visitGeneration()).isZero();
        String completed = "{\"mapId\":\"33333333-3333-3333-3333-333333333333\",\"setId\":\"jiri\","
            + "\"explorerId\":\"11111111-1111-1111-1111-111111111111\",\"completedAt\":\"2026-10-02T03:00:02Z\"}";
        var setCompleted = om.readValue(completed, com.kobi.territory.progression.api.event.SetCompleted.class);
        assertThat(setCompleted.completedBy()).isNull();
        assertThat(setCompleted.recipientIds()).isNull();
        assertThat(setCompleted.explorerId()).isEqualTo("11111111-1111-1111-1111-111111111111");
    }
}
