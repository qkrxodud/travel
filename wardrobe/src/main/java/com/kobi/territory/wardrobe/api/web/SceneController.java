package com.kobi.territory.wardrobe.api.web;

import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.wardrobe.api.web.WardrobeDtos.ItemResponse;
import com.kobi.territory.wardrobe.api.web.WardrobeDtos.SceneRequest;
import com.kobi.territory.wardrobe.api.web.WardrobeDtos.SceneResponse;
import com.kobi.territory.wardrobe.application.SceneService;
import com.kobi.territory.wardrobe.application.SceneView;
import com.kobi.territory.wardrobe.domain.item.EquipSlot;
import com.kobi.territory.wardrobe.domain.scene.Scene;
import com.kobi.territory.wardrobe.domain.scene.SceneEdit;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 장면(꾸미기) API — 착용·벗기·성별·장식. 꾸미기 점수는 서버가 계산해 준다. */
@RestController
@RequestMapping("/scene")
public class SceneController {

    private final SceneService scenes;

    public SceneController(SceneService scenes) {
        this.scenes = scenes;
    }

    @GetMapping
    public SceneResponse scene(@CurrentExplorer ExplorerId explorerId) {
        return response(scenes.view(explorerId));
    }

    @PutMapping
    public SceneResponse edit(@CurrentExplorer ExplorerId explorerId, @Valid @RequestBody SceneRequest request) {
        return response(scenes.edit(explorerId,
            new SceneEdit(request.gender(), request.equip(), request.unequip(), request.props())));
    }

    private static SceneResponse response(SceneView view) {
        Scene scene = view.scene();
        Map<String, ItemView> worn = view.wornItems().stream()
            .collect(Collectors.toMap(ItemView::itemId, Function.identity()));
        Map<String, ItemResponse> slots = new LinkedHashMap<>();
        for (EquipSlot slot : EquipSlot.values()) {
            slots.put(slot.name(), scene.equippedSlots().itemAt(slot).map(itemId -> ItemResponse.of(itemId, worn.get(itemId)))
                .orElse(null));
        }
        return new SceneResponse(scene.gender().name(), slots,
            scene.propSlots().itemIds().stream().map(itemId -> ItemResponse.of(itemId, worn.get(itemId))).toList(),
            view.stylePoints(), scene.wornItemIds().size(), scene.updatedAt());
    }
}
