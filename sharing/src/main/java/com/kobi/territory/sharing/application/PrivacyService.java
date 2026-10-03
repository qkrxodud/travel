package com.kobi.territory.sharing.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.sharing.api.query.ProfileVisibilityQuery;
import com.kobi.territory.sharing.domain.privacy.PrivacySettings;
import com.kobi.territory.sharing.domain.privacy.PrivacySettingsRepository;
import com.kobi.territory.sharing.domain.privacy.ProfileVisibility;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 범위(PrivacySettings) 유스케이스 + 공개 Query({@link ProfileVisibilityQuery}). 행이 없으면 기본값(PRIVATE, 사용자 결정 Q1). */
@Service
public class PrivacyService implements ProfileVisibilityQuery {

    private final PrivacySettingsRepository settings;
    private final TerritoryQuery territories;
    private final Clock clock;

    public PrivacyService(PrivacySettingsRepository settings, TerritoryQuery territories, Clock clock) {
        this.settings = settings;
        this.territories = territories;
        this.clock = clock;
    }

    /** GET /me/privacy — 탐험가가 없으면 404 EXPLORER_NOT_FOUND. */
    @Transactional(readOnly = true)
    public PrivacySettings view(ExplorerId explorerId) {
        territories.personalMapId(explorerId.value());
        return load(explorerId);
    }

    /** PUT /me/privacy. */
    @Transactional
    public PrivacySettings change(ExplorerId explorerId, String visibility) {
        territories.personalMapId(explorerId.value());
        PrivacySettings current = load(explorerId);
        current.change(ProfileVisibility.parse(visibility), clock.instant());
        settings.save(current);
        return current;
    }

    /** 공개 경로용: 공개가 아니면 PROFILE_NOT_FOUND(존재 숨김). */
    @Transactional(readOnly = true)
    public void requireVisibleToPublic(ExplorerId explorerId) {
        load(explorerId).requireVisibleToPublic();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean visibleToPublic(String explorerId) {
        return load(ExplorerId.of(explorerId)).visibleToPublic();
    }

    private PrivacySettings load(ExplorerId explorerId) {
        return settings.find(explorerId).orElseGet(() -> PrivacySettings.defaults(explorerId));
    }
}
