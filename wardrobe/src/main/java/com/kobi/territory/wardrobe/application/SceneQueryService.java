package com.kobi.territory.wardrobe.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.wardrobe.api.query.SceneQuery;
import com.kobi.territory.wardrobe.api.query.SceneSummaryView;
import com.kobi.territory.wardrobe.api.query.SceneSummaryView.WornItemView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link SceneQuery} 구현 — 장면 조회(SceneService)와 가방 조회(InventoryService)를 공개 DTO 로 옮긴다. */
@Service
public class SceneQueryService implements SceneQuery {

    private final SceneService scenes;
    private final InventoryService inventories;

    public SceneQueryService(SceneService scenes, InventoryService inventories) {
        this.scenes = scenes;
        this.inventories = inventories;
    }

    @Override
    @Transactional(readOnly = true)
    public SceneSummaryView summaryOf(String explorerId) {
        ExplorerId explorer = ExplorerId.of(explorerId);
        SceneView view = scenes.view(explorer);
        return new SceneSummaryView(view.scene().gender().name(), view.stylePoints(),
            view.wornItems().stream().map(item -> new WornItemView(item.itemId(), item.name(), item.slot(), item.tier().name()))
                .toList(),
            inventories.view(explorer).ownedItems().count());
    }
}
