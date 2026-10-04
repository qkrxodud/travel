package com.kobi.territory.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.catalog.api.query.RewardLineView;
import com.kobi.territory.catalog.application.CatalogService;
import com.kobi.territory.catalog.application.ItemDefinitionCache;
import com.kobi.territory.catalog.domain.catalog.Catalog;
import com.kobi.territory.catalog.infra.repository.JsonCatalogRepository;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 실제로 싣는 진행 정의 데이터(레벨·테마·뱃지·퀘스트·칭호, 원본: 프로토타입 → gen-catalog.js). Spring 없음. */
@DisplayName("진행 정의 데이터")
class ProgressionDataTest {

    private static final Catalog 데이터 = new JsonCatalogRepository().load();
    private static final CatalogService 카탈로그 = new CatalogService(() -> 데이터,
        new ItemDefinitionCache(InMemoryItemDefinitionRepository.empty()));

    @Test
    @DisplayName("다른 컨텍스트가 묻는 보상 계산은 카탈로그의 같은 보상 규칙을 쓴다")
    void publicRewardQuery() {
        assertThat(카탈로그.checkIn(Rarity.RARE, true, true)).containsExactly(new RewardLineView("REGION_BASE", 20),
            new RewardLineView("PROVINCE_FIRST", 15), new RewardLineView("FIRST_CLAIM", 10));
        assertThat(카탈로그.setComplete().amount()).isEqualTo(100);
    }

    @Test
    @DisplayName("레벨 곡선 계수는 5이고 레벨 칭호는 기획한 여섯 단계다")
    void levels() {
        assertThat(카탈로그.levelDivisor()).isEqualTo(5);
        assertThat(카탈로그.levelTitles()).extracting(ProgressionRules.LevelTitleView::name)
            .containsExactly("초보 탐험가", "동네 산책러", "길 위의 사람", "전국 유랑객", "팔도 정복자", "영토의 주인");
    }

    @Nested
    @DisplayName("도감 테마는")
    class Themes {

        @Test
        @DisplayName("아홉 개가 정해진 순서로 있다")
        void nineInOrder() {
            assertThat(카탈로그.sets()).extracting(ProgressionRules.SetView::id)
                .containsExactly("east", "sea", "old", "island", "ball", "soup", "dmz", "jiri", "han");
        }

        @Test
        @DisplayName("테마마다 이름과 모을 지역이 정해져 있다")
        void themeContents() {
            var jiri = 카탈로그.sets().stream().filter(set -> set.id().equals("jiri")).findFirst().orElseThrow();

            assertThat(jiri.title()).isEqualTo("산 사람");
            assertThat(jiri.regionCodes()).containsExactly("KR-35050", "KR-36330", "KR-38360", "KR-38370", "KR-38380");
            assertThat(카탈로그.sets().stream().mapToInt(set -> set.regionCodes().size()).sum())
                .isEqualTo(9 + 6 + 6 + 8 + 9 + 6 + 8 + 5 + 10);
        }

        @Test
        @DisplayName("카탈로그에 있는 지역만 가리킨다")
        void knownRegionsOnly() {
            카탈로그.sets().forEach(set -> set.regionCodes().forEach(regionCode ->
                assertThat(카탈로그.findRegion(RegionCode.of(regionCode))).as(regionCode).isPresent()));
        }
    }

    @Test
    @DisplayName("뱃지는 열아홉 개(기획 열두 개 + 미스터리 탐험가 세 단계 + 단골 여행자 두 단계 + 꿈을 이룬 여행자 두 단계)이고 조건은 기획한 그대로다")
    void badges() {
        assertThat(카탈로그.badges()).hasSize(19);
        Map<String, ProgressionRules.BadgeView> byId = 카탈로그.badges().stream()
            .collect(Collectors.toMap(ProgressionRules.BadgeView::id, badge -> badge));
        assertThat(byId.get("ten").condition()).extracting("type", "min").containsExactly("REGION_COUNT", 10);
        assertThat(byId.get("capital").condition().provinces()).containsExactly("KR-11", "KR-31", "KR-23");
        assertThat(byId.get("samnam").condition().groups()).hasSize(3);
        assertThat(byId.get("half").condition().ratio()).isEqualTo(0.5);
        assertThat(byId.get("streak3").condition().type()).isEqualTo("STREAK_MONTHS");
        assertThat(List.of(byId.get("mystery1"), byId.get("mystery5"), byId.get("mystery10")))
            .extracting(badge -> badge.condition().type() + ":" + badge.condition().min())
            .containsExactly("MYSTERY_FOUND:1", "MYSTERY_FOUND:5", "MYSTERY_FOUND:10");
        assertThat(List.of(byId.get("revisit5"), byId.get("revisit20"), byId.get("wish3"), byId.get("wish10")))
            .extracting(badge -> badge.name() + ":" + badge.condition().type() + ":" + badge.condition().min())
            .containsExactly("단골 여행자:REVISIT_STAMPS:5", "오랜 단골:REVISIT_STAMPS:20", "꿈을 이룬 여행자:WISHES_FULFILLED:3",
                "꿈의 수집가:WISHES_FULFILLED:10");
    }

    @Test
    @DisplayName("퀘스트는 월간 넷, 상시 도전 셋이다")
    void quests() {
        assertThat(카탈로그.quests()).extracting(quest -> quest.id() + ":" + quest.scope() + ":" + quest.target() + ":" + quest.xp())
            .containsExactly("m3:MONTHLY:3:60", "mgun:MONTHLY:1:40", "mprov:MONTHLY:1:80", "mset:MONTHLY:2:50",
                "leg5:ALWAYS:5:150", "gun30:ALWAYS:30:150", "p3:ALWAYS:17:200");
        assertThat(카탈로그.quests().stream().filter(quest -> quest.id().equals("p3")).findFirst().orElseThrow().param())
            .isEqualTo(3);
    }

    @Test
    @DisplayName("칭호는 레벨 6·테마 9·상시 도전 3·연속 탐험 4·계절 2·시·도 17개다")
    void titles() {
        assertThat(카탈로그.titles()).hasSize(6 + 9 + 3 + 4 + 2 + 17);
        assertThat(카탈로그.titles()).extracting(ProgressionRules.TitleView::id)
            .contains("lv1", "lv16", "set-jiri", "long-leg5", "streak-3", "streak-24", "season-spring", "season-autumn", "own-KR-11");
        assertThat(카탈로그.titles().stream().filter(title -> title.id().equals("own-KR-11")).findFirst().orElseThrow().name())
            .isEqualTo("서울의 주인");
    }

    @Nested
    @DisplayName("8단계 게임 규칙 값")
    class GameRules {

        @Test
        @DisplayName("보호권은 최대 두 개이고 한 달 월간 퀘스트를 모두 받으면 하나다")
        void freeze() {
            assertThat(카탈로그.streakRules().freezeMaxHeld()).isEqualTo(2);
            assertThat(카탈로그.streakRules().monthlyQuestsFreezes()).isEqualTo(1);
        }

        @Test
        @DisplayName("연속 탐험 마일스톤은 3·6·12·24개월에 XP 50·100·200·400과 보호권 하나다")
        void milestones() {
            assertThat(카탈로그.streakRules().milestones())
                .extracting(milestone -> milestone.months() + ":" + milestone.xp() + ":" + milestone.freezes() + ":" + milestone.titleId())
                .containsExactly("3:50:1:streak-3", "6:100:1:streak-6", "12:200:1:streak-12", "24:400:1:streak-24");
        }

        @Test
        @DisplayName("이번 주 미스터리 보너스는 50, 시·도 정복 보상은 300이다")
        void bonuses() {
            assertThat(카탈로그.mysteryBonus()).isEqualTo(new RewardLineView("MYSTERY_BONUS", 50));
            assertThat(카탈로그.provinceConquest()).isEqualTo(new RewardLineView("PROVINCE_CONQUEST", 300));
            assertThat(카탈로그.checkIn(Rarity.LEGEND, false, false, true)).extracting(RewardLineView::source)
                .containsExactly("REGION_BASE", "MYSTERY_BONUS");
        }

        @Test
        @DisplayName("미스터리 지역은 희귀·전설에서 방문자 비율 하위 30% 안에서 고른다")
        void mysteryRules() {
            assertThat(데이터.mystery().rarities()).containsExactlyInAnyOrder(Rarity.RARE, Rarity.LEGEND);
            assertThat(데이터.mystery().bottomFraction()).isEqualTo(0.3);
        }
    }

    @Nested
    @DisplayName("9단계 게임 규칙 값")
    class SecondGameRules {

        @Test
        @DisplayName("계절 한정 테마는 봄 벚꽃 명소(3/20~4/30)와 가을 단풍 명소(10/1~11/30) 열 곳씩이다")
        void seasons() {
            assertThat(카탈로그.seasons()).extracting(season -> season.id() + ":" + season.name() + ":" + season.start() + "~" + season.end()
                    + ":" + season.regionCodes().size() + ":" + season.titleId())
                .containsExactly("spring:벚꽃 명소:03-20~04-30:10:season-spring", "autumn:단풍 명소:10-01~11-30:10:season-autumn");
        }

        @Test
        @DisplayName("계절 지역은 모두 카탈로그에 있는 현행 지역이다")
        void seasonRegionsKnown() {
            카탈로그.seasons().forEach(season -> season.regionCodes().forEach(regionCode ->
                assertThat(카탈로그.findRegion(RegionCode.of(regionCode))).as(regionCode).isPresent()));
        }

        @Test
        @DisplayName("계절 완성 150, 재방문 도장 10, 가고 싶은 곳 다녀옴 20 이다")
        void bonuses() {
            assertThat(카탈로그.seasonComplete()).isEqualTo(new RewardLineView("SEASON_COMPLETE", 150));
            assertThat(카탈로그.revisitStamp()).isEqualTo(new RewardLineView("REVISIT_STAMP", 10));
            assertThat(카탈로그.wishFulfilled()).isEqualTo(new RewardLineView("WISH_FULFILLED", 20));
            assertThat(카탈로그.rewardRules().seasonCompleteBonus()).isEqualTo(150);
        }
    }
}
