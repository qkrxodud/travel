package com.kobi.territory.wardrobe.domain.scene;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Optional;

/** 장면 저장소(scene 한 행). version 으로 낙관적 락 — 사용자 편집과 자동 착용(이벤트)이 겹치면 한쪽이 충돌로 다시 시도한다. */
public interface SceneRepository {

    Optional<Scene> find(ExplorerId explorerId);

    void save(Scene scene);
}
