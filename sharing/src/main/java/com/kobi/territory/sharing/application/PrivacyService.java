package com.kobi.territory.sharing.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.sharing.api.query.ProfileVisibilityQuery;
import com.kobi.territory.sharing.domain.privacy.PrivacyRoster;
import com.kobi.territory.sharing.domain.privacy.PrivacySettings;
import com.kobi.territory.sharing.domain.privacy.PrivacySettingsRepository;
import com.kobi.territory.sharing.domain.privacy.ProfileVisibility;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 범위(PrivacySettings) 유스케이스 + 공개 Query({@link ProfileVisibilityQuery}). 행이 없으면 기본값(PRIVATE, 사용자 결정 Q1).
 * FRIENDS(5단계)의 친구 판정은 {@link FriendDirectory} 포트(소셜 맞팔로우)로 묻는다 — 판단(누구에게 보이는지)은 PrivacySettings 가 한다.
 */
@Service
public class PrivacyService implements ProfileVisibilityQuery {

    private final PrivacySettingsRepository settings;
    private final TerritoryQuery territories;
    private final FriendDirectory friends;
    private final Clock clock;

    public PrivacyService(PrivacySettingsRepository settings, TerritoryQuery territories, FriendDirectory friends, Clock clock) {
        this.settings = settings;
        this.territories = territories;
        this.friends = friends;
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

    /** 공개 경로용(5단계): viewer(익명이면 null)에게 보이지 않으면 PROFILE_NOT_FOUND(존재 숨김). */
    @Transactional(readOnly = true)
    public void requireVisibleTo(ExplorerId owner, ExplorerId viewer) {
        load(owner).requireVisibleTo(viewer, candidate -> friends.mutualFriends(owner.value(), candidate.value()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean visibleToPublic(String explorerId) {
        return load(ExplorerId.of(explorerId)).visibleToPublic();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean visibleTo(String ownerId, String viewerIdOrNull) {
        ExplorerId viewer = viewerIdOrNull == null ? null : ExplorerId.of(viewerIdOrNull);
        return load(ExplorerId.of(ownerId)).visibleTo(viewer, candidate -> friends.mutualFriends(ownerId, candidate.value()));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> visibleAmong(Collection<String> ownerIds, String viewerId) {
        List<ExplorerId> owners = ownerIds.stream().map(ExplorerId::of).toList();
        return PrivacyRoster.of(owners, settings.findAll(owners))
            .visibleTo(ExplorerId.of(viewerId), (owner, viewer) -> friends.mutualFriends(owner.value(), viewer.value()))
            .stream().map(ExplorerId::value).collect(Collectors.toUnmodifiableSet());
    }

    private PrivacySettings load(ExplorerId explorerId) {
        return settings.find(explorerId).orElseGet(() -> PrivacySettings.defaults(explorerId));
    }
}
