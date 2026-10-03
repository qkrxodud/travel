package com.kobi.territory.wardrobe.application;

import com.kobi.territory.catalog.api.query.ItemCatalog;
import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.progression.api.query.CompletedSetView;
import com.kobi.territory.wardrobe.domain.item.GrantKind;
import com.kobi.territory.wardrobe.domain.item.ItemSlot;
import com.kobi.territory.wardrobe.domain.item.ItemSpec;
import com.kobi.territory.wardrobe.domain.item.ItemSpecs;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 카탈로그(상류) 아이템 Query → 꾸미기 도메인 ItemSpec 변환 어댑터(Anti-Corruption Layer). 아이템 정의는 운영이 수시로
 * 추가하므로(DB) 시작 시 고정하지 않고 필요할 때 읽는다. 지급 규칙 판정은 카탈로그가 한다.
 * 기간 판정은 체크인 처리 시각·테마 완성 시각으로 한다(카탈로그가 서버 시간대 날짜로 본다) — 사용자가 적은 방문일이나
 * 재계산 시각이 아니다(소급 지급 없음, Q-R2-1).
 */
@Component
public class WardrobeCatalog {

    private final ItemCatalog items;

    public WardrobeCatalog(ItemCatalog items) {
        this.items = items;
    }

    /** 처리 시각 visitedAt 의 체크인으로 받는 아이템(이슈 아이템은 정의가 생긴 뒤의 체크인만 — 소급 없음, Q-R2-1). */
    public List<ItemSpec> grantedByCheckIn(String regionCode, String provinceCode, Instant visitedAt) {
        return items.grantedByCheckIn(regionCode, provinceCode, visitedAt).stream().map(WardrobeCatalog::specOf).toList();
    }

    /** 테마 완성 보상 — 기간은 완성 시각으로 판정(Q-R2-1). */
    public List<ItemSpec> grantedByThemeCompletion(String setId, Instant completedAt) {
        return items.grantedByThemeCompletion(setId, completedAt).stream().map(WardrobeCatalog::specOf).toList();
    }

    /** 지도에서 이미 완성된 테마들의 보상(지도 합류·재계산) — 각 테마의 완성 시각으로 판정. */
    public List<ItemSpec> grantedByThemeCompletions(List<CompletedSetView> completedSets) {
        return completedSets.stream()
            .flatMap(completed -> grantedByThemeCompletion(completed.setId(), completed.completedAt()).stream()).toList();
    }

    /** 초대 합류 보상 — side = HOST(초대한 쪽) | GUEST(초대받은 쪽), 기간은 합류 시각으로 판정(4단계). */
    public List<ItemSpec> grantedByInvitation(String side, Instant joinedAt) {
        return items.grantedByInvitation(side, joinedAt).stream().map(WardrobeCatalog::specOf).toList();
    }

    public ItemSpecs specsOf(Collection<String> itemIds) {
        return ItemSpecs.of(views(itemIds).stream().map(WardrobeCatalog::specOf).toList());
    }

    /** 화면 표시용 정의(이름·이모지·룩). */
    public List<ItemView> views(Collection<String> itemIds) {
        return items.items(itemIds);
    }

    static ItemSpec specOf(ItemView view) {
        return new ItemSpec(view.itemId(), ItemSlot.valueOf(view.slot()), view.tier(), GrantKind.valueOf(view.grantRule()));
    }
}
