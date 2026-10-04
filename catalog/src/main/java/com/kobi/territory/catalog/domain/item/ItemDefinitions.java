package com.kobi.territory.catalog.domain.item;

import com.kobi.territory.catalog.domain.CatalogError;
import com.kobi.territory.catalog.domain.region.Regions;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 일급 컬렉션: 아이템 정의. itemId 유일, 지역 아이템(region:{code}) 조회, 지역마다 아이템이 있는지 검증,
 * 지급 규칙 판정(체크인·테마 완성으로 받을 아이템).
 */
public final class ItemDefinitions {

    private final Map<String, ItemDefinition> byId;

    private ItemDefinitions(Collection<ItemDefinition> items) {
        Map<String, ItemDefinition> map = new LinkedHashMap<>();
        for (ItemDefinition item : items) {
            if (map.put(item.itemId(), item) != null) throw new IllegalStateException("아이템 중복: " + item.itemId());
        }
        this.byId = map;
    }

    public static ItemDefinitions of(Collection<ItemDefinition> items) {
        return new ItemDefinitions(items);
    }

    public Optional<ItemDefinition> find(String itemId) {
        return Optional.ofNullable(byId.get(itemId));
    }

    public Optional<ItemDefinition> regionItem(RegionCode code) {
        return find(ItemDefinition.regionItemId(code));
    }

    /**
     * 운영 추가 전 확인: 이관 데이터의 예약 id(region:·set: — 지역 특산물·세트 배경)는 쓸 수 없고(INVALID_ITEM_DEFINITION),
     * 같은 id 가 이미 있으면 ITEM_ALREADY_EXISTS. 예약 접두어 덕분에 dev 초기화가 운영 추가분만 골라 지울 수 있다.
     */
    public void requireRegistrable(ItemDefinition item) {
        if (item.migrated()) {
            throw CatalogError.INVALID_ITEM_DEFINITION.exception("region:·set:·invite:·conquest:·streak: 으로 시작하는 id 는 이관 데이터 전용입니다: " + item.itemId());
        }
        if (byId.containsKey(item.itemId())) throw CatalogError.ITEM_ALREADY_EXISTS.exception(item.itemId());
    }

    public void requireCoverage(Regions regions) {
        regions.all().forEach(region -> regionItem(region.code())
            .orElseThrow(() -> new IllegalStateException("지역 아이템 누락: " + region.code())));
    }

    /** 처리 시각 processedAt(날짜 day)의 체크인 한 번으로 받는 아이템(지역 특산물 + 이슈 아이템, 소급 없음). */
    public List<ItemDefinition> grantedByCheckIn(RegionCode region, String provinceCode, LocalDate day, Instant processedAt) {
        return stream().filter(item -> item.grantedByCheckIn(region, provinceCode, day, processedAt)).toList();
    }

    /** 완성 시각 completedAt(날짜 day)의 테마(세트) 완성으로 받는 아이템(세트 배경 + 정의 생성 뒤 완성분의 운영 추가 보상). */
    public List<ItemDefinition> grantedByThemeCompletion(String themeId, LocalDate day, Instant completedAt) {
        return stream().filter(item -> item.grantedByThemeCompletion(themeId, day, completedAt)).toList();
    }

    /** 정복 시각 conqueredAt(날짜 day)의 시·도 정복으로 받는 아이템(8단계). */
    public List<ItemDefinition> grantedByProvinceConquest(String provinceCode, LocalDate day, Instant conqueredAt) {
        return stream().filter(item -> item.grantedByProvinceConquest(provinceCode, day, conqueredAt)).toList();
    }

    /** 도달 시각 reachedAt(날짜 day)의 연속 탐험 마일스톤으로 받는 아이템(8단계). */
    public List<ItemDefinition> grantedByStreakMilestone(int months, LocalDate day, Instant reachedAt) {
        return stream().filter(item -> item.grantedByStreakMilestone(months, day, reachedAt)).toList();
    }

    /** 이 날짜의 초대 합류에서 그 쪽(HOST·GUEST)이 받는 아이템. */
    public List<ItemDefinition> grantedBySeasonCompletion(String roundId, LocalDate day, Instant completedAt) {
        return stream().filter(item -> item.grantedBySeasonCompletion(roundId, day, completedAt)).toList();
    }

    public List<ItemDefinition> grantedByInvitation(GrantRule.InvitationSide side, LocalDate day) {
        return stream().filter(item -> item.grantedByInvitation(side, day)).toList();
    }

    /** 주어진 id 들 중 정의가 있는 것(요청 순서). */
    public List<ItemDefinition> findAll(Collection<String> itemIds) {
        return itemIds.stream().distinct().map(byId::get).filter(item -> item != null).toList();
    }

    public Stream<ItemDefinition> stream() {
        return byId.values().stream();
    }

    public int size() {
        return byId.size();
    }
}
