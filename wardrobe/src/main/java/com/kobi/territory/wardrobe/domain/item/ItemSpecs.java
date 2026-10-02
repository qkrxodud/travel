package com.kobi.territory.wardrobe.domain.item;

import com.kobi.territory.wardrobe.domain.WardrobeError;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 일급 컬렉션: 이번 커맨드가 참조하는 아이템 사양들(카탈로그에서 그때그때 읽어 온다). */
public final class ItemSpecs {

    private final Map<String, ItemSpec> byId;

    private ItemSpecs(Collection<ItemSpec> specs) {
        Map<String, ItemSpec> map = new LinkedHashMap<>();
        specs.forEach(spec -> map.putIfAbsent(spec.itemId(), spec));
        this.byId = map;
    }

    public static ItemSpecs of(Collection<ItemSpec> specs) {
        return new ItemSpecs(specs);
    }

    public Optional<ItemSpec> find(String itemId) {
        return Optional.ofNullable(byId.get(itemId));
    }

    /** 없으면 404 ITEM_NOT_FOUND(사용자 입력 검증). */
    public ItemSpec require(String itemId) {
        return find(itemId).orElseThrow(() -> WardrobeError.ITEM_NOT_FOUND.exception(itemId));
    }
}
