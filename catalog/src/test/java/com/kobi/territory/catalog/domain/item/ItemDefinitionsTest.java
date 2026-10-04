package com.kobi.territory.catalog.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.catalog.domain.region.Region;
import com.kobi.territory.catalog.domain.region.Regions;
import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 아이템 정의와 지급 규칙(지역 특산물·기간 이슈·시·도 이슈·테마 완성·수동). 지급 날짜는 처리 시각 기준.
 * 회귀 출처: 3단계 리더 결정 Q-R2-1(소급 지급 없음), QA P3-R3-3(운영 추가 테마 보상).
 */
@DisplayName("아이템 정의")
class ItemDefinitionsTest {

    private static final ZoneId 서울시각 = ZoneId.of("Asia/Seoul");
    private static final RegionCode 종로구 = RegionCode.of("KR-11010");
    private static final RegionCode 해운대구 = RegionCode.of("KR-21090");
    /** 정의를 만든 시각: 2026-10-02 정오(KST). */
    private static final Instant 정의시각 = 정오(LocalDate.of(2026, 10, 2));
    private static final LocalDate 십월삼일 = LocalDate.of(2026, 10, 3);

    private static Instant 정오(LocalDate day) {
        return day.atTime(12, 0).atZone(서울시각).toInstant();
    }

    private static ItemDefinition 아이템(String itemId, ItemSlot slot, GrantRule rule, ValidPeriod period) {
        return new ItemDefinition(itemId, "이름", "*", slot, Rarity.RARE, null, null, rule, period, 정의시각);
    }

    private static final ItemDefinition 종로_청사초롱 = 아이템("region:KR-11010", ItemSlot.HAND, new GrantRule.RegionVisit(종로구), null);
    /** 10월 1일 ~ 9일 기간 이슈. */
    private static final ItemDefinition 추석_갓 = 아이템("event:chuseok", ItemSlot.HAT, new GrantRule.PeriodCheckIn(),
        new ValidPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9)));
    private static final ItemDefinition 서울_핀 = 아이템("event:seoul-pin", ItemSlot.BADGE, new GrantRule.ProvinceCheckIn("KR-11"), null);
    private static final ItemDefinition 한강_배경 = 아이템("set:han", ItemSlot.BG, new GrantRule.ThemeComplete("han"), null);
    private static final ItemDefinition 선물 = 아이템("event:gift", ItemSlot.PET, new GrantRule.Manual(), null);
    private static final ItemDefinitions 전부 = ItemDefinitions.of(List.of(종로_청사초롱, 추석_갓, 서울_핀, 한강_배경, 선물));

    private static List<String> 체크인으로받는것(RegionCode region, String province, LocalDate day, Instant at) {
        return 전부.grantedByCheckIn(region, province, day, at).stream().map(ItemDefinition::itemId).toList();
    }

    @Nested
    @DisplayName("체크인 한 번으로")
    class CheckIn {

        @Test
        @DisplayName("그 지역 특산물, 기간 이슈, 시·도 이슈를 함께 받는다")
        void regionPeriodProvince() {
            assertThat(체크인으로받는것(종로구, "KR-11", 십월삼일, 정오(십월삼일)))
                .containsExactly("region:KR-11010", "event:chuseok", "event:seoul-pin");
        }

        @Test
        @DisplayName("다른 시·도의 지역에서는 기간 이슈만 받는다")
        void otherProvincePeriodOnly() {
            assertThat(체크인으로받는것(해운대구, "KR-21", 십월삼일, 정오(십월삼일))).containsExactly("event:chuseok");
        }

        @Test
        @DisplayName("기간 이슈는 기간 마지막 날까지 받는다")
        void lastDayIncluded() {
            LocalDate 마지막날 = LocalDate.of(2026, 10, 9);

            assertThat(체크인으로받는것(해운대구, "KR-21", 마지막날, 정오(마지막날))).hasSize(1);
        }

        @Test
        @DisplayName("기간이 지나면 기간 이슈를 받지 않는다")
        void afterPeriod() {
            LocalDate 다음날 = LocalDate.of(2026, 10, 10);

            assertThat(체크인으로받는것(해운대구, "KR-21", 다음날, 정오(다음날))).isEmpty();
        }

        @Test
        @DisplayName("이슈 아이템은 정의가 생기기 전의 체크인에 소급해 주지 않는다")
        void noRetroactiveEventItems() {
            Instant 정의전아침 = LocalDate.of(2026, 10, 2).atTime(9, 0).atZone(서울시각).toInstant();

            assertThat(체크인으로받는것(종로구, "KR-11", LocalDate.of(2026, 10, 2), 정의전아침)).containsExactly("region:KR-11010");
        }

        @Test
        @DisplayName("정의가 생긴 시각부터의 체크인은 이슈 아이템을 받는다")
        void fromDefinitionTime() {
            assertThat(체크인으로받는것(종로구, "KR-11", LocalDate.of(2026, 10, 2), 정의시각))
                .containsExactly("region:KR-11010", "event:chuseok", "event:seoul-pin");
        }

        @Test
        @DisplayName("수동 지급 아이템은 체크인으로 받지 않는다")
        void manualNeverAuto() {
            assertThat(전부.stream().filter(item -> item.grantedByCheckIn(종로구, "KR-11", 십월삼일, 정오(십월삼일))))
                .doesNotContain(선물);
        }
    }

    @Nested
    @DisplayName("테마를 완성하면")
    class ThemeCompletion {

        @Test
        @DisplayName("그 테마의 보상만 받는다")
        void thatThemeOnly() {
            assertThat(전부.grantedByThemeCompletion("han", 십월삼일, 정오(십월삼일))).containsExactly(한강_배경);
            assertThat(전부.grantedByThemeCompletion("jiri", 십월삼일, 정오(십월삼일))).isEmpty();
        }

        @Test
        @DisplayName("운영이 나중에 더한 테마 보상은 정의가 생긴 뒤의 완성에만 준다")
        void addedRewardNotRetroactive() {
            ItemDefinition 한강_보너스 = 아이템("event:han-bonus", ItemSlot.BADGE, new GrantRule.ThemeComplete("han"), null);
            ItemDefinitions withBonus = ItemDefinitions.of(List.of(한강_배경, 한강_보너스));

            assertThat(withBonus.grantedByThemeCompletion("han", LocalDate.of(2026, 10, 2), 정의시각.minusSeconds(60)))
                .doesNotContain(한강_보너스);
            assertThat(withBonus.grantedByThemeCompletion("han", LocalDate.of(2026, 10, 2), 정의시각))
                .containsExactly(한강_배경, 한강_보너스);
        }

        @Test
        @DisplayName("테마 배경은 정의 시각보다 앞선 완성에도 준다")
        void themeBackgroundAlways() {
            assertThat(전부.grantedByThemeCompletion("han", LocalDate.of(2026, 10, 2), 정의시각.minusSeconds(60)))
                .containsExactly(한강_배경);
        }
    }

    @Nested
    @DisplayName("시·도를 정복하면")
    class ProvinceConquest {

        private final ItemDefinition 서울_기념비 = 아이템("conquest:KR-11", ItemSlot.PROP, new GrantRule.ProvinceComplete("KR-11"), null);
        private final ItemDefinition 운영_서울_깃발 = 아이템("event:seoul-flag", ItemSlot.HAND, new GrantRule.ProvinceComplete("KR-11"),
            null);
        private final ItemDefinitions 정복보상 = ItemDefinitions.of(List.of(서울_기념비, 운영_서울_깃발, 종로_청사초롱));

        @Test
        @DisplayName("그 시·도의 대표 장식만 받는다")
        void onlyThatProvince() {
            assertThat(정복보상.grantedByProvinceConquest("KR-21", 십월삼일, 정오(십월삼일))).isEmpty();
            assertThat(정복보상.grantedByProvinceConquest("KR-11", 십월삼일, 정오(십월삼일))).extracting(ItemDefinition::itemId)
                .containsExactly("conquest:KR-11", "event:seoul-flag");
        }

        @Test
        @DisplayName("운영이 나중에 더한 정복 보상은 정의가 생긴 뒤의 정복에만 준다")
        void operatorAddedNotRetroactive() {
            LocalDate 시월일일 = LocalDate.of(2026, 10, 1);

            assertThat(정복보상.grantedByProvinceConquest("KR-11", 시월일일, 정오(시월일일))).extracting(ItemDefinition::itemId)
                .containsExactly("conquest:KR-11");
        }

        @Test
        @DisplayName("체크인으로는 정복 보상을 받지 않는다")
        void notByCheckIn() {
            assertThat(정복보상.grantedByCheckIn(종로구, "KR-11", 십월삼일, 정오(십월삼일))).extracting(ItemDefinition::itemId)
                .containsExactly("region:KR-11010");
        }
    }

    @Nested
    @DisplayName("연속 탐험 마일스톤에 닿으면")
    class StreakMilestone {

        private final ItemDefinitions 마일스톤보상 = ItemDefinitions.of(List.of(
            아이템("streak:3", ItemSlot.BADGE, new GrantRule.StreakMilestone(3), null),
            아이템("streak:6", ItemSlot.HAND, new GrantRule.StreakMilestone(6), null)));

        @Test
        @DisplayName("그 개월 수의 한정 아이템만 받는다")
        void onlyThatMilestone() {
            assertThat(마일스톤보상.grantedByStreakMilestone(3, 십월삼일, 정오(십월삼일))).extracting(ItemDefinition::itemId)
                .containsExactly("streak:3");
        }

        @Test
        @DisplayName("마일스톤 규칙의 대상은 1 이상의 개월 수다")
        void monthsRequired() {
            assertThatThrownBy(() -> GrantRule.of(GrantRule.Type.STREAK_MILESTONE, "three"))
                .isInstanceOf(TerritoryException.class);
            assertThatThrownBy(() -> GrantRule.of(GrantRule.Type.STREAK_MILESTONE, "0")).isInstanceOf(TerritoryException.class);
        }
    }

    @Nested
    @DisplayName("계절 한정 테마 회차를 완성하면")
    class SeasonComplete {

        private final ItemDefinitions 계절배경 = ItemDefinitions.of(List.of(
            아이템("season:autumn-2026", ItemSlot.BG, new GrantRule.SeasonComplete("autumn-2026"), null),
            아이템("season:autumn-2027", ItemSlot.BG, new GrantRule.SeasonComplete("autumn-2027"), null)));

        @Test
        @DisplayName("그 회차의 배경만 받는다")
        void onlyThatRound() {
            assertThat(계절배경.grantedBySeasonCompletion("autumn-2026", 십월삼일, 정오(십월삼일))).extracting(ItemDefinition::itemId)
                .containsExactly("season:autumn-2026");
        }

        @Test
        @DisplayName("회차 규칙의 대상은 계절-연도 형식이다")
        void roundIdRequired() {
            assertThatThrownBy(() -> GrantRule.of(GrantRule.Type.SEASON_COMPLETE, "autumn")).isInstanceOf(TerritoryException.class);
            assertThat(GrantRule.of(GrantRule.Type.SEASON_COMPLETE, "spring-2027").ref()).isEqualTo("spring-2027");
        }

        @Test
        @DisplayName("체크인이나 테마 완성으로는 계절 배경을 받지 않는다")
        void notByOthers() {
            assertThat(계절배경.grantedByCheckIn(종로구, "KR-11", 십월삼일, 정오(십월삼일))).isEmpty();
            assertThat(계절배경.grantedByThemeCompletion("autumn-2026", 십월삼일, 정오(십월삼일))).isEmpty();
        }
    }

    @Nested
    @DisplayName("재방문 2회차 색 변형")
    class RevisitVariant {

        @Test
        @DisplayName("지역 특산물은 주색과 보조색을 바꾼 룩으로 그린다")
        void swapped() {
            ItemDefinition 청사초롱 = new ItemDefinition("region:KR-11010", "청사초롱", "*", ItemSlot.HAND, Rarity.COMMON, null,
                new ItemDefinition.Look("lantern", "#e63946", "#f4c542"), new GrantRule.RegionVisit(종로구), null, 정의시각);

            assertThat(청사초롱.revisitVariantLook()).isEqualTo(new ItemDefinition.Look("lantern", "#f4c542", "#e63946"));
        }

        @Test
        @DisplayName("지역 특산물이 아니면 변형이 없다")
        void onlyRegionItems() {
            assertThat(한강_배경.revisitVariantLook()).isNull();
            assertThat(선물.revisitVariantLook()).isNull();
        }
    }

    @Nested
    @DisplayName("정의 목록은")
    class Definitions {

        private final ItemDefinitions 청사초롱만 = ItemDefinitions.of(List.of(종로_청사초롱));

        @Test
        @DisplayName("지역으로 그 지역 특산물을 찾는다")
        void regionItem() {
            assertThat(청사초롱만.regionItem(종로구)).isPresent();
            assertThat(청사초롱만.find("region:KR-11020")).isEmpty();
        }

        @Test
        @DisplayName("특산물이 없는 지역이 있으면 받지 않는다")
        void coverage() {
            Regions 중구만 = Regions.of(List.of(new Region(RegionCode.of("KR-11020"), "중구", "KR-11", Rarity.COMMON, "KR", 1,
                null, null)));

            assertThatThrownBy(() -> 청사초롱만.requireCoverage(중구만)).hasMessageContaining("지역 아이템 누락");
        }

        @Test
        @DisplayName("같은 아이템이 두 번 있으면 받지 않는다")
        void duplicate() {
            assertThatThrownBy(() -> ItemDefinitions.of(List.of(종로_청사초롱, 종로_청사초롱))).hasMessageContaining("아이템 중복");
        }
    }

    @Nested
    @DisplayName("운영이 정의를 입력할 때")
    class Validation {

        @Test
        @DisplayName("아이템 id 형식이 틀리면 받지 않는다")
        void badId() {
            assertThatThrownBy(() -> 아이템("bad id", ItemSlot.HAT, new GrantRule.Manual(), null))
                .isInstanceOf(TerritoryException.class).hasFieldOrPropertyWithValue("code", "INVALID_ITEM_DEFINITION");
        }

        @Test
        @DisplayName("기간 이슈에 유효 기간이 없으면 받지 않는다")
        void periodRequired() {
            assertThatThrownBy(() -> 아이템("event:x", ItemSlot.HAT, new GrantRule.PeriodCheckIn(), null))
                .hasMessageContaining("유효 기간");
        }

        @Test
        @DisplayName("끝나는 날이 시작하는 날보다 앞서면 받지 않는다")
        void reversedPeriod() {
            assertThatThrownBy(() -> new ValidPeriod(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 1)))
                .hasMessageContaining("앞입니다");
        }

        @Test
        @DisplayName("색이 올바른 색상 값이 아니면 정의를 받지 않는다")
        void colorFormat() {
            assertThatThrownBy(() -> new ItemDefinition.Look("lantern", "red", "#ffffff")).hasMessageContaining("#rrggbb");
        }

        @Test
        @DisplayName("시·도 정복·연속 탐험 아이템 id 는 이관 데이터 전용이라 운영이 쓸 수 없다")
        void reservedPrefixes() {
            ItemDefinitions 비어있음 = ItemDefinitions.of(List.of());

            assertThatThrownBy(() -> 비어있음.requireRegistrable(아이템("conquest:KR-99", ItemSlot.PROP,
                new GrantRule.ProvinceComplete("KR-11"), null))).isInstanceOf(TerritoryException.class);
            assertThatThrownBy(() -> 비어있음.requireRegistrable(아이템("streak:36", ItemSlot.HAT,
                new GrantRule.StreakMilestone(36), null))).isInstanceOf(TerritoryException.class);
        }

        @Test
        @DisplayName("시·도 정복 보상은 시·도 코드를 가리켜야 한다")
        void conquestNeedsProvince() {
            assertThatThrownBy(() -> GrantRule.of(GrantRule.Type.PROVINCE_COMPLETE, "seoul")).isInstanceOf(TerritoryException.class);
        }

        @Test
        @DisplayName("시·도 이슈는 시·도 코드를 가리켜야 한다")
        void provinceReference() {
            assertThatThrownBy(() -> GrantRule.of(GrantRule.Type.PROVINCE_CHECK_IN, "서울")).hasMessageContaining("시·도 코드");
        }

        @Test
        @DisplayName("테마 보상은 테마를 가리켜야 한다")
        void themeReference() {
            assertThatThrownBy(() -> GrantRule.of(GrantRule.Type.THEME_COMPLETE, " ")).hasMessageContaining("세트 id");
        }

        @Test
        @DisplayName("이름이 40자를 넘으면 받지 않는다")
        void nameTooLong() {
            assertThatThrownBy(() -> new ItemDefinition("event:x", "x".repeat(41), "*", ItemSlot.HAT, Rarity.RARE, null, null,
                new GrantRule.Manual(), null, 정의시각)).hasMessageContaining("이름");
        }
    }
}
