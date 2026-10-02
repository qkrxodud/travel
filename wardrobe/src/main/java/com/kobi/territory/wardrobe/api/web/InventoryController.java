package com.kobi.territory.wardrobe.api.web;

import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.wardrobe.api.web.WardrobeDtos.FavoriteRequest;
import com.kobi.territory.wardrobe.api.web.WardrobeDtos.InventoryResponse;
import com.kobi.territory.wardrobe.api.web.WardrobeDtos.OwnedItemResponse;
import com.kobi.territory.wardrobe.application.InventoryService;
import com.kobi.territory.wardrobe.application.SceneService;
import com.kobi.territory.wardrobe.application.WardrobeCatalog;
import com.kobi.territory.wardrobe.domain.inventory.Inventory;
import com.kobi.territory.wardrobe.domain.inventory.OwnedItem;
import com.kobi.territory.wardrobe.domain.scene.Scene;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 가방(보유 아이템) API. 아이템 생김새는 카탈로그 정의(DB)에서 붙인다. */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventories;
    private final SceneService scenes;
    private final WardrobeCatalog catalog;

    public InventoryController(InventoryService inventories, SceneService scenes, WardrobeCatalog catalog) {
        this.inventories = inventories;
        this.scenes = scenes;
        this.catalog = catalog;
    }

    /** 보유 아이템(최근에 얻은 순) + 착용 여부. */
    @GetMapping
    public InventoryResponse inventory(@CurrentExplorer ExplorerId explorerId) {
        Inventory inventory = inventories.view(explorerId);
        Scene scene = scenes.view(explorerId).scene();
        List<OwnedItem> owned = inventory.ownedItems().newestFirst();
        Map<String, ItemView> views = viewsOf(owned.stream().map(OwnedItem::itemId).toList());
        return new InventoryResponse(owned.size(),
            owned.stream().map(item -> OwnedItemResponse.of(item, views.get(item.itemId()), scene.wears(item.itemId()))).toList());
    }

    @PutMapping("/{itemId}/favorite")
    public OwnedItemResponse favorite(@CurrentExplorer ExplorerId explorerId, @PathVariable("itemId") String itemId,
                                      @Valid @RequestBody FavoriteRequest request) {
        OwnedItem item = inventories.markFavorite(explorerId, itemId, request.favorite());
        Scene scene = scenes.view(explorerId).scene();
        return OwnedItemResponse.of(item, viewsOf(List.of(itemId)).get(itemId), scene.wears(itemId));
    }

    private Map<String, ItemView> viewsOf(List<String> itemIds) {
        return catalog.views(itemIds).stream().collect(Collectors.toMap(ItemView::itemId, Function.identity()));
    }
}
