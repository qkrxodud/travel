package com.kobi.territory.sharing.domain.privacy;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 공개 범위 설정 저장소(privacy_settings). 행이 없으면 기본값({@link PrivacySettings#defaults}). */
public interface PrivacySettingsRepository {

    Optional<PrivacySettings> find(ExplorerId explorerId);

    /** 여러 탐험가의 저장된 설정(행이 있는 것만, 5단계 — 피드 공개 범위). */
    List<PrivacySettings> findAll(Collection<ExplorerId> explorerIds);

    void save(PrivacySettings settings);
}
