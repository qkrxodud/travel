package com.kobi.territory.sharing.domain.privacy;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Optional;

/** 공개 범위 설정 저장소(privacy_settings). 행이 없으면 기본값({@link PrivacySettings#defaults}). */
public interface PrivacySettingsRepository {

    Optional<PrivacySettings> find(ExplorerId explorerId);

    void save(PrivacySettings settings);
}
