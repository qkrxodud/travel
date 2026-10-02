package com.kobi.territory.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.catalog.domain.Catalog;
import com.kobi.territory.catalog.domain.ItemDefinition;
import com.kobi.territory.catalog.domain.ItemDefinitions;
import com.kobi.territory.catalog.domain.ItemSlot;
import com.kobi.territory.catalog.domain.Province;
import com.kobi.territory.catalog.domain.Provinces;
import com.kobi.territory.catalog.domain.Region;
import com.kobi.territory.catalog.domain.Regions;
import com.kobi.territory.catalog.domain.RewardRules;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 카탈로그 일급 컬렉션(Regions·Provinces·ItemDefinitions)과 Catalog 정합성 — Spring 없음. */
class CatalogCollectionsTest {

    static Region region(String code, String prov, LocalDate retired) {
        return new Region(RegionCode.of(code), "r" + code, prov, Rarity.COMMON, "KR", 1, null, retired);
    }

    static ItemDefinition item(String code) {
        return new ItemDefinition("region:" + code, RegionCode.of(code), "i", "*", ItemSlot.HAND, Rarity.COMMON, null, null);
    }

    static final RewardRules RULES = new RewardRules(Map.of(Rarity.COMMON, 10, Rarity.RARE, 20, Rarity.LEGEND, 50), 15, 100, 10);

    @Test
    void Regions는_코드_중복을_거부하고_현행_지역과_시도별_수를_준다() {
        Regions regions = Regions.of(List.of(region("KR-11010", "KR-11", null), region("KR-11020", "KR-11", null),
            region("KR-37310", "KR-37", LocalDate.of(2023, 7, 1))));
        assertThat(regions.active()).extracting(r -> r.code().value()).containsExactly("KR-11010", "KR-11020");
        assertThat(regions.activeCountByProvince()).containsExactly(Map.entry("KR-11", 2));
        assertThat(regions.find(RegionCode.of("KR-37310"))).get().extracting(Region::active).isEqualTo(false);
        assertThatThrownBy(() -> Regions.of(List.of(region("KR-11010", "KR-11", null), region("KR-11010", "KR-11", null))))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("중복");
    }

    @Test
    void Provinces는_표시_순서로_정렬하고_지역_수_정합성을_검증한다() {
        Provinces provinces = Provinces.of(List.of(new Province("KR-31", "경기", "경기도", 2, 1),
            new Province("KR-11", "서울", "서울특별시", 1, 1)));
        assertThat(provinces.inDisplayOrder()).extracting(Province::name).containsExactly("서울", "경기");
        provinces.requireConsistentWith(Regions.of(List.of(region("KR-11010", "KR-11", null), region("KR-31011", "KR-31", null))));
        assertThatThrownBy(() -> provinces.requireConsistentWith(Regions.of(List.of(region("KR-11010", "KR-11", null)))))
            .hasMessageContaining("지역 수 불일치");
        assertThatThrownBy(() -> provinces.requireConsistentWith(Regions.of(List.of(region("KR-39010", "KR-39", null)))))
            .hasMessageContaining("모르는 시·도");
    }

    @Test
    void ItemDefinitions는_id_유일_지역_아이템_조회_누락_검증() {
        ItemDefinitions items = ItemDefinitions.of(List.of(item("KR-11010")));
        assertThat(items.regionItem(RegionCode.of("KR-11010"))).isPresent();
        assertThat(items.find("region:KR-11020")).isEmpty();
        assertThatThrownBy(() -> items.requireCoverage(Regions.of(List.of(region("KR-11020", "KR-11", null)))))
            .hasMessageContaining("지역 아이템 누락");
        assertThatThrownBy(() -> ItemDefinitions.of(List.of(item("KR-11010"), item("KR-11010"))))
            .hasMessageContaining("아이템 중복");
    }

    @Test
    void Catalog는_생성_시_정합성을_검증한다() {
        Regions regions = Regions.of(List.of(region("KR-11010", "KR-11", null)));
        Provinces provinces = Provinces.of(List.of(new Province("KR-11", "서울", "서울특별시", 1, 1)));
        new Catalog(regions, provinces, ItemDefinitions.of(List.of(item("KR-11010"))), RULES, "{}");
        assertThatThrownBy(() -> new Catalog(regions, provinces, ItemDefinitions.of(List.of()), RULES, "{}"))
            .isInstanceOf(IllegalStateException.class);
    }
}
