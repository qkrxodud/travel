package com.kobi.territory.wardrobe.domain.item;

import static com.kobi.territory.wardrobe.domain.Fixtures.regionItem;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.Rarity;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("아이템 사양")
class ItemSpecTest {

    @Nested
    @DisplayName("희귀도 비교")
    class Rarer {

        @Test
        @DisplayName("전설은 희귀보다, 희귀는 일반보다 희귀하다")
        void ordered() {
            ItemSpec common = regionItem("KR-11010", ItemSlot.HAND, Rarity.COMMON);
            ItemSpec rare = regionItem("KR-11020", ItemSlot.HAND, Rarity.RARE);
            ItemSpec legend = regionItem("KR-11030", ItemSlot.HAND, Rarity.LEGEND);

            assertThat(legend.rarerThan(rare)).isTrue();
            assertThat(rare.rarerThan(common)).isTrue();
        }

        @Test
        @DisplayName("희귀도가 같으면 더 희귀하지 않다")
        void sameRarityIsNotRarer() {
            assertThat(regionItem("KR-11010", ItemSlot.HAND, Rarity.RARE).rarerThan(regionItem("KR-11020", ItemSlot.HAND, Rarity.RARE)))
                .isFalse();
        }
    }

    @Nested
    @DisplayName("받는 방식")
    class GrantKinds {

        @Test
        @DisplayName("방문으로 받는 것은 지역 특산물·기간 체크인·시·도 체크인 아이템뿐이다")
        void byVisit() {
            assertThat(Arrays.stream(GrantKind.values()).filter(GrantKind::byVisit))
                .containsExactlyInAnyOrder(GrantKind.REGION_VISIT, GrantKind.PERIOD_CHECK_IN, GrantKind.PROVINCE_CHECK_IN);
        }

        @Test
        @DisplayName("다시 계산으로 되살릴 수 있는 것은 방문형과 테마 완성 아이템이고 직접 받은 것·초대 보상은 아니다")
        void replayable() {
            assertThat(Arrays.stream(GrantKind.values()).filter(GrantKind::replayable))
                .containsExactlyInAnyOrder(GrantKind.REGION_VISIT, GrantKind.PERIOD_CHECK_IN, GrantKind.PROVINCE_CHECK_IN,
                    GrantKind.THEME_COMPLETE);
        }
    }
}
