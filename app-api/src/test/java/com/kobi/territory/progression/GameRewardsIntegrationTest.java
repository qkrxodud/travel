package com.kobi.territory.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.application.RecalculateService;
import com.kobi.territory.support.Explorers;
import com.kobi.territory.support.Explorers.Anonymous;
import com.kobi.territory.support.Explorers.Session;
import com.kobi.territory.support.IntegrationTest;
import com.kobi.territory.support.MutableClock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 8단계 게임 요소 1순위 — 보호권·연속 탐험 마일스톤·이번 주 미스터리 지역·시·도 정복이 체크인 소식을 따라 진행·가방·친구 소식까지
 * 이어지는 이야기(MockMvc + 릴레이). 달·주 넘김은 테스트 시계로 옮기고 끝나면 되돌린다.
 */
@IntegrationTest
@DisplayName("게임 보상 — 보호권·마일스톤·미스터리·시·도 정복")
class GameRewardsIntegrationTest {

    private static final String 세종 = "KR-29010";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired MockMvc mvc;
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

    private JsonNode 진행(Anonymous who) throws Exception {
        return json(explorers.기기로(who, get("/progress")).andExpect(status().isOk()));
    }

    private JsonNode 이번주_미스터리(Anonymous who) throws Exception {
        return json(explorers.기기로(who, get("/mystery/this-week")).andExpect(status().isOk()));
    }

    private List<String> 가방(Anonymous who) throws Exception {
        List<String> itemIds = new ArrayList<>();
        json(explorers.기기로(who, get("/inventory")).andExpect(status().isOk())).get("items")
            .forEach(item -> itemIds.add(item.get("itemId").asText()));
        return itemIds;
    }

    private void 진행이(Anonymous who, Predicate<JsonNode> condition) {
        await().atMost(WAIT).until(() -> condition.test(진행(who)));
    }

    private static JsonNode 시도(JsonNode progress, String provinceCode) {
        for (JsonNode province : progress.get("provinces")) {
            if (province.get("code").asText().equals(provinceCode)) return province;
        }
        throw new AssertionError("시·도 없음: " + provinceCode);
    }

    /** 다음 달(또는 n달 뒤) 15일 정오(서울 시각)로 시계를 옮긴다. */
    private void 달이_지난다(int months) {
        YearMonth month = YearMonth.from(clock.instant().atZone(clock.getZone())).plusMonths(months);
        clock.set(month.atDay(15).atTime(12, 0).atZone(clock.getZone()).toInstant());
    }

    /** 다음 월요일 0시(서울 시각)로 시계를 옮긴다. */
    private void 다음_주가_된다() {
        ZonedDateTime now = clock.instant().atZone(clock.getZone());
        clock.set(now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(clock.getZone()).toInstant());
    }

    // ---- 이야기 ------------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("이번 주 미스터리 지역")
    class Mystery {

        @Test
        @DisplayName("누구에게나 같은 희귀·전설 지역을 보여 주고 남은 기간과 보너스를 알려 준다")
        void sameForEveryone() throws Exception {
            JsonNode mine = 이번주_미스터리(explorers.익명_탐험가());
            JsonNode theirs = 이번주_미스터리(explorers.익명_탐험가());

            assertThat(theirs.get("region").get("code").asText()).isEqualTo(mine.get("region").get("code").asText());
            assertThat(mine.get("region").get("rarity").asText()).isIn("RARE", "LEGEND");
            assertThat(mine.get("bonusXp").asInt()).isEqualTo(50);
            assertThat(mine.get("remainingSeconds").asLong()).isPositive().isLessThanOrEqualTo(7 * 24 * 3600);
            assertThat(mine.get("received").asBoolean()).isFalse();
            assertThat(mine.get("revealed").asBoolean()).isFalse();
            assertThat(LocalDate.parse(mine.get("weekStart").asText()).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        }

        @Test
        @DisplayName("그 지역을 고르면 미리보기에 보너스 줄이 보이고, 칠하면 보너스와 첫 미스터리 뱃지를 받는다")
        void paintMystery() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            String mystery = 이번주_미스터리(me).get("region").get("code").asText();

            JsonNode preview = json(explorers.기기로(me, get("/visits/preview").param("region", mystery)).andExpect(status().isOk()));
            assertThat(preview.get("xp").get("lines")).anySatisfy(line -> {
                assertThat(line.get("source").asText()).isEqualTo("MYSTERY_BONUS");
                assertThat(line.get("amount").asInt()).isEqualTo(50);
            });

            explorers.칠한다(me, mystery);
            await().atMost(WAIT).until(() -> 이번주_미스터리(me).get("received").asBoolean());

            assertThat(이번주_미스터리(me).get("revealed").asBoolean()).isTrue();
            JsonNode progress = 진행(me);
            assertThat(progress.get("mysteryFoundCount").asInt()).isEqualTo(1);
            assertThat(progress.get("badges")).anySatisfy(badge -> {
                assertThat(badge.get("id").asText()).isEqualTo("mystery1");
                assertThat(badge.get("earned").asBoolean()).isTrue();
            });
        }

        @Test
        @DisplayName("고른 방문일이 지난 주여도 이번 주에 칠하면 보너스를 받는다")
        void processedThisWeek() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            String mystery = 이번주_미스터리(me).get("region").get("code").asText();

            explorers.체크인(me, Explorers.방문(mystery, LocalDate.now(clock).minusDays(10), null, null))
                .andExpect(status().isCreated());

            await().atMost(WAIT).until(() -> 이번주_미스터리(me).get("received").asBoolean());
        }

        @Test
        @DisplayName("다음 주 월요일 0시가 되면 새 주가 시작되고 그 주의 보너스는 아직 받지 않은 상태다")
        void nextWeek() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            JsonNode thisWeek = 이번주_미스터리(me);
            explorers.칠한다(me, thisWeek.get("region").get("code").asText());
            await().atMost(WAIT).until(() -> 이번주_미스터리(me).get("received").asBoolean());

            다음_주가_된다();
            JsonNode next = 이번주_미스터리(me);

            assertThat(LocalDate.parse(next.get("weekStart").asText()))
                .isEqualTo(LocalDate.parse(thisWeek.get("weekStart").asText()).plusWeeks(1));
            assertThat(next.get("received").asBoolean()).isFalse();
            assertThat(진행(me).get("mysteryFoundCount").asInt()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("시·도 정복")
    class Conquest {

        @Test
        @DisplayName("세종의 유일한 지역을 칠하면 정복 보상 300 XP와 세종 대표 장식을 받고 정복 기록이 남는다")
        void conquerSejong() throws Exception {
            Anonymous me = explorers.익명_탐험가();

            explorers.칠한다(me, 세종);
            진행이(me, progress -> 시도(progress, "KR-29").get("conquered").asBoolean());

            JsonNode progress = 진행(me);
            assertThat(progress.get("recentXp")).anySatisfy(entry -> {
                assertThat(entry.get("source").asText()).isEqualTo("PROVINCE_CONQUEST");
                assertThat(entry.get("amount").asInt()).isEqualTo(300);
            });
            assertThat(시도(progress, "KR-29").get("percent").asInt()).isEqualTo(100);
            await().atMost(WAIT).untilAsserted(() -> assertThat(가방(me)).contains("conquest:KR-29"));
        }

        @Test
        @DisplayName("칠한 곳을 취소해도 정복 기록과 대표 장식은 남는다")
        void keptAfterCancel() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            explorers.칠한다(me, 세종);
            await().atMost(WAIT).untilAsserted(() -> assertThat(가방(me)).contains("conquest:KR-29"));

            explorers.기기로(me, delete("/visits/" + 세종)).andExpect(status().isNoContent());
            explorers.전달이_끝날_때까지();

            JsonNode sejong = 시도(진행(me), "KR-29");
            assertThat(sejong.get("complete").asBoolean()).isFalse();
            assertThat(sejong.get("conquered").asBoolean()).isTrue();
            assertThat(가방(me)).contains("conquest:KR-29").doesNotContain("region:" + 세종);
        }
    }

    @Nested
    @DisplayName("연속 탐험과 보호권")
    class StreakAndFreeze {

        @Test
        @DisplayName("석 달 연속 칠하면 마일스톤 XP·칭호·보호권·한정 아이템을 받는다")
        void threeMonthMilestone() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            explorers.칠한다(me, "KR-11010");
            달이_지난다(1);
            explorers.칠한다(me, "KR-11020");
            달이_지난다(1);

            explorers.칠한다(me, "KR-11030");
            진행이(me, progress -> progress.get("streakFreeze").get("held").asInt() == 1);

            JsonNode progress = 진행(me);
            assertThat(progress.get("streak").get("months").asInt()).isEqualTo(3);
            assertThat(progress.get("milestones").get(0).get("reached").asBoolean()).isTrue();
            assertThat(progress.get("nextMilestone").get("months").asInt()).isEqualTo(6);
            assertThat(progress.get("titles")).anySatisfy(title -> {
                assertThat(title.get("id").asText()).isEqualTo("streak-3");
                assertThat(title.get("earned").asBoolean()).isTrue();
            });
            await().atMost(WAIT).untilAsserted(() -> assertThat(가방(me)).contains("streak:3"));
        }

        @Test
        @DisplayName("한 달을 비운 뒤 칠하면 보호권 하나로 스트릭을 지키고 그 기록이 보인다")
        void freezeKeepsStreak() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            explorers.칠한다(me, "KR-11040");
            달이_지난다(1);
            explorers.칠한다(me, "KR-11050");
            달이_지난다(1);
            explorers.칠한다(me, "KR-11060");
            진행이(me, progress -> progress.get("streakFreeze").get("held").asInt() == 1);

            달이_지난다(1);
            assertThat(진행(me).get("streak").get("freezesNeeded").asInt()).isZero();
            달이_지난다(1); // 한 달이 빈 채로 다음 달
            JsonNode waiting = 진행(me);
            assertThat(waiting.get("streak").get("months").asInt()).isEqualTo(3);
            assertThat(waiting.get("streak").get("freezesNeeded").asInt()).isEqualTo(1);

            explorers.칠한다(me, "KR-11070");
            진행이(me, progress -> progress.get("streakFreeze").get("held").asInt() == 0);

            JsonNode progress = 진행(me);
            assertThat(progress.get("streak").get("months").asInt()).isEqualTo(4);
            assertThat(progress.get("streakFreeze").get("lastUsed").get("count").asInt()).isEqualTo(1);
            assertThat(progress.get("streakFreeze").get("lastUsed").get("month").asText())
                .isEqualTo(YearMonth.from(clock.instant().atZone(clock.getZone())).toString());
        }

        @Test
        @DisplayName("처음부터 다시 세어도 보호권·마일스톤·연속 개월이 그대로다")
        void recalculationKeepsSame() throws Exception {
            Anonymous me = explorers.익명_탐험가();
            explorers.칠한다(me, "KR-11080");
            달이_지난다(1);
            explorers.칠한다(me, "KR-11090");
            달이_지난다(1);
            explorers.칠한다(me, "KR-11100"); // 3개월 — 마일스톤 보호권
            달이_지난다(2); // 한 달 빔 — 보호권으로 이음
            explorers.칠한다(me, "KR-11110");
            explorers.전달이_끝날_때까지();
            JsonNode before = 진행(me);

            recalculate.recalculate(ExplorerId.of(me.id()));
            JsonNode after = 진행(me);

            assertThat(after.get("xp")).isEqualTo(before.get("xp"));
            assertThat(after.get("streak")).isEqualTo(before.get("streak"));
            assertThat(after.get("streakFreeze")).isEqualTo(before.get("streakFreeze"));
            assertThat(after.get("milestones")).isEqualTo(before.get("milestones"));
        }
    }

    @Nested
    @DisplayName("친구 소식")
    class Feed {

        private List<String> 소식(Session viewer) throws Exception {
            List<String> kinds = new ArrayList<>();
            json(explorers.세션으로(viewer, get("/feed")).andExpect(status().isOk())).get("items")
                .forEach(item -> kinds.add(item.get("kind").asText() + ":" + item.get("provinceCode").asText(null)));
            return kinds;
        }

        @Test
        @DisplayName("공개한 친구가 시·도를 정복하면 내 소식에 보이고, 비공개로 바꾸면 사라진다")
        void conquestNews() throws Exception {
            Session 이 = explorers.로그인(null, Explorers.새_이메일("conquer"));
            Session 김 = explorers.로그인(null, Explorers.새_이메일("watcher"));
            explorers.공개_범위(이, "PUBLIC");
            explorers.세션으로(김, post("/friends/" + 이.handle())).andExpect(status().isCreated());

            explorers.칠한다(이, null, 세종);
            await().atMost(WAIT).untilAsserted(() -> assertThat(소식(김)).contains("PROVINCE_CONQUERED:KR-29"));

            explorers.공개_범위(이, "PRIVATE");
            assertThat(소식(김)).doesNotContain("PROVINCE_CONQUERED:KR-29");
        }
    }

    @Nested
    @DisplayName("익명 탐험가를 계정으로 합치면")
    class Merge {

        @Test
        @DisplayName("익명 시절 장부의 보상·보호권은 옮기지 않고, 합친 영토로 다시 세어 계정이 정복을 받는다")
        void ledgerNotMoved() throws Exception {
            String email = Explorers.새_이메일("merge8");
            Session account = explorers.로그인(null, email);
            Anonymous device = explorers.익명_탐험가();
            explorers.칠한다(device, 세종);
            await().atMost(WAIT).untilAsserted(() -> assertThat(가방(device)).contains("conquest:KR-29"));

            Session merged = explorers.로그인(device, email);
            explorers.전달이_끝날_때까지();

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND ref_id LIKE ?", Integer.class,
                account.explorerId(), "%" + device.id() + "%")).as("계정 장부에 익명 시절 기록").isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM streak_freeze WHERE explorer_id = ? AND ref_id LIKE ?", Integer.class,
                account.explorerId(), "%" + device.id() + "%")).as("계정 보호권 장부에 익명 시절 기록").isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND ref_id = ?", Integer.class,
                device.id(), "conquest:" + device.id() + ":KR-29")).as("익명 시절 장부는 그대로").isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM xp_ledger WHERE explorer_id = ? AND ref_id = ?", Integer.class,
                account.explorerId(), "conquest:" + account.explorerId() + ":KR-29")).as("합친 영토로 다시 센 정복").isEqualTo(1);
            assertThat(merged.explorerId()).isEqualTo(account.explorerId());
        }
    }
}
