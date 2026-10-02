package com.kobi.territory.wardrobe.infra.repository;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.wardrobe.domain.scene.Scene;
import com.kobi.territory.wardrobe.domain.scene.SceneRepository;
import com.kobi.territory.wardrobe.infra.entity.SceneJpaEntity;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Scene 저장소 어댑터(scene 한 행). 있으면 갱신(version 검사·증가), 없으면 추가. 변환은 엔티티가 한다. */
@Repository
class JpaSceneRepository implements SceneRepository {

    private final SceneJpaRepository rows;

    JpaSceneRepository(SceneJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<Scene> find(ExplorerId explorerId) {
        return rows.findById(explorerId.value()).map(SceneJpaEntity::toDomain);
    }

    @Override
    public void save(Scene scene) {
        rows.findById(scene.explorerId().value()).ifPresentOrElse(
            row -> row.apply(scene),
            () -> rows.save(SceneJpaEntity.from(scene)));
    }
}
