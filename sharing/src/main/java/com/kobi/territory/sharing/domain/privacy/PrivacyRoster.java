package com.kobi.territory.sharing.domain.privacy;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * 일급 컬렉션: 여러 주인의 공개 범위 설정(5단계 — 소셜 피드가 "이 친구들 중 누구의 활동을 보여 줄지" 한 번에 묻는다).
 * 저장된 행이 없는 주인은 기본값(PRIVATE)으로 채운다.
 */
public final class PrivacyRoster {

    private final Map<ExplorerId, PrivacySettings> byOwner;

    private PrivacyRoster(Map<ExplorerId, PrivacySettings> byOwner) {
        this.byOwner = Map.copyOf(byOwner);
    }

    /** owners 마다 저장된 설정(stored)이 있으면 그것, 없으면 기본값. */
    public static PrivacyRoster of(Collection<ExplorerId> owners, Collection<PrivacySettings> stored) {
        Map<ExplorerId, PrivacySettings> byOwner = new LinkedHashMap<>();
        owners.forEach(owner -> byOwner.put(owner, PrivacySettings.defaults(owner)));
        stored.stream().filter(settings -> byOwner.containsKey(settings.explorerId()))
            .forEach(settings -> byOwner.put(settings.explorerId(), settings));
        return new PrivacyRoster(byOwner);
    }

    /** viewer 에게 프로필이 보이는 주인들. mutualFriends(주인, viewer) = 서로 팔로우한 친구인지. */
    public Set<ExplorerId> visibleTo(ExplorerId viewer, BiPredicate<ExplorerId, ExplorerId> mutualFriends) {
        return byOwner.values().stream()
            .filter(settings -> settings.visibleTo(viewer, candidate -> mutualFriends.test(settings.explorerId(), candidate)))
            .map(PrivacySettings::explorerId)
            .collect(Collectors.toUnmodifiableSet());
    }
}
