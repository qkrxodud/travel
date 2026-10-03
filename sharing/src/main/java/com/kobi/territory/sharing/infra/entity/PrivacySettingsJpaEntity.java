package com.kobi.territory.sharing.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.sharing.domain.privacy.PrivacySettings;
import com.kobi.territory.sharing.domain.privacy.ProfileVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** privacy_settings — 공개 범위(PrivacySettings). 행이 없으면 기본값 PUBLIC. */
@Entity
@Table(name = "privacy_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrivacySettingsJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Column(name = "visibility", nullable = false, length = 8)
    private String visibility;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static PrivacySettingsJpaEntity from(PrivacySettings settings) {
        PrivacySettingsJpaEntity entity = new PrivacySettingsJpaEntity();
        entity.explorerId = settings.explorerId().value();
        entity.apply(settings);
        return entity;
    }

    public void apply(PrivacySettings settings) {
        this.visibility = settings.visibility().name();
        this.updatedAt = settings.updatedAt();
    }

    public PrivacySettings toDomain() {
        return PrivacySettings.restore(ExplorerId.of(explorerId), ProfileVisibility.valueOf(visibility), updatedAt);
    }
}
