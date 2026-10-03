package com.kobi.territory.sharing.infra.repository;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.sharing.domain.privacy.PrivacySettings;
import com.kobi.territory.sharing.domain.privacy.PrivacySettingsRepository;
import com.kobi.territory.sharing.infra.entity.PrivacySettingsJpaEntity;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** PrivacySettings 저장소 어댑터 — 있으면 갱신, 없으면 넣기. */
@Repository
class JpaPrivacySettingsRepository implements PrivacySettingsRepository {

    private final PrivacySettingsJpaRepository rows;

    JpaPrivacySettingsRepository(PrivacySettingsJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<PrivacySettings> find(ExplorerId explorerId) {
        return rows.findById(explorerId.value()).map(PrivacySettingsJpaEntity::toDomain);
    }

    @Override
    public void save(PrivacySettings settings) {
        rows.findById(settings.explorerId().value()).ifPresentOrElse(row -> row.apply(settings),
            () -> rows.save(PrivacySettingsJpaEntity.from(settings)));
    }
}
