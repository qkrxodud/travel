package com.kobi.territory.progression.domain.collectionbook;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 도감 애그리거트(mapId — 지도 단위). 테마별 진행·완성. 설계 용어 Collection(도감) ↔ 코드 CollectionBook,
 * 세트(CollectionSet·SetProgress) ↔ Theme·ThemeProgress (JDK 자료형 이름과 겹치지 않게 — 명명 규칙).
 *
 * 불변식
 * - 완성은 정의된 지역이 한 지도에 모두 모였을 때 단 한 번(completedAt 은 한 번 정해지면 바뀌지 않는다).
 * - 완성 후 지역을 취소해도 완성 기록 유지(보상 회수 없음). 진행(collected)에서는 빠진다.
 * - 지도에 그 지역이 다른 멤버 방문으로 남아 있으면(regionStillOnMap) 진행에서도 빼지 않는다(D2).
 * - 완성 보상 수령자는 완성 시점의 지도 멤버 전원(결정 1). 완성 기록과 함께 남긴다(재계산 복구 규칙의 기준 — R2-1).
 */
public final class CollectionBook {

    private final String mapId;
    private final Map<String, ThemeProgress> progressByTheme;

    private CollectionBook(String mapId, Collection<ThemeProgress> restored) {
        this.mapId = Objects.requireNonNull(mapId, "mapId");
        this.progressByTheme = new LinkedHashMap<>();
        restored.forEach(themeProgress -> progressByTheme.put(themeProgress.themeId(), themeProgress));
    }

    public static CollectionBook empty(String mapId) {
        return new CollectionBook(mapId, List.of());
    }

    public static CollectionBook restore(String mapId, Collection<ThemeProgress> restored) {
        return new CollectionBook(mapId, restored);
    }

    /**
     * 지역이 칠해짐 → 테마 진행, 처음 완성된 테마를 돌려준다. 같은 지역이 다시 와도 변화 없음(멱등).
     *
     * @param members 칠한 시점의 지도 멤버(완성 보상 수령자). 비었으면(예전 이벤트) 칠한 사람만
     */
    public List<ThemeCompletion> applyVisit(RegionCode region, ExplorerId visitor, Instant at, Themes themes,
                                            Collection<ExplorerId> members) {
        Set<ExplorerId> recipients = new LinkedHashSet<>(members);
        recipients.add(visitor);
        List<ThemeCompletion> completions = new ArrayList<>();
        for (Theme theme : themes.containing(region)) {
            ThemeProgress next = progressOf(theme.id()).with(region);
            if (!next.completed() && theme.completedBy(next.collected())) {
                next = next.completedAt(at, recipients);
                completions.add(new ThemeCompletion(mapId, theme.id(), visitor, at, List.copyOf(recipients)));
            }
            progressByTheme.put(theme.id(), next);
        }
        return List.copyOf(completions);
    }

    /** 혼자인 지도(개인 지도)에서 칠함 — 수령자는 칠한 사람뿐. */
    public List<ThemeCompletion> applyVisit(RegionCode region, ExplorerId visitor, Instant at, Themes themes) {
        return applyVisit(region, visitor, at, themes, List.of(visitor));
    }

    /** 지역 방문 취소. 지도에 그 지역이 남아 있지 않을 때만 진행에서 뺀다. 완성 기록은 유지. */
    public void revokeVisit(RegionCode region, boolean regionStillOnMap, Themes themes) {
        if (regionStillOnMap) return;
        themes.containing(region).forEach(theme ->
            progressByTheme.computeIfPresent(theme.id(), (themeId, themeProgress) -> themeProgress.without(region)));
    }

    /** 탈퇴로 지도에서 사라진 지역들(VisitsHidden.regionsGoneFromMap)을 진행에서 뺀다. 완성 기록은 유지. 멱등. */
    public void revokeRegions(Collection<RegionCode> regionsGone, Themes themes) {
        regionsGone.forEach(region -> revokeVisit(region, false, themes));
    }

    /** 재가입 복구로 지도에 다시 칠해진 지역들 — 다시 진행에 넣고, 그로 인해 처음 완성된 테마를 돌려준다(수령자 = 복구 시점 멤버). */
    public List<ThemeCompletion> restoreRegions(Collection<RegionCode> regionsBack, ExplorerId restoredMember, Instant at,
                                                Themes themes, Collection<ExplorerId> members) {
        List<ThemeCompletion> completions = new ArrayList<>();
        regionsBack.forEach(region -> completions.addAll(applyVisit(region, restoredMember, at, themes, members)));
        return List.copyOf(completions);
    }

    /** 재계산용: 완성 기록(completedAt)은 남기고 모은 지역만 비운 도감 — 완성은 한 번이고 취소해도 유지되는 불변식 때문. */
    public CollectionBook rebuildBase() {
        return new CollectionBook(mapId, progressByTheme.values().stream().map(ThemeProgress::withoutCollected).toList());
    }

    /** 완성 기록이 있는 테마 id. */
    public List<String> completedThemeIds() {
        return progressByTheme.values().stream().filter(ThemeProgress::completed).map(ThemeProgress::themeId).toList();
    }

    /** 이 탐험가가 완성 보상 수령자(완성 시점 멤버)인 테마 id — 재계산 복구 규칙의 기준(결정 1·R2-1). */
    public List<String> themeIdsRewardedTo(ExplorerId explorer) {
        return progressByTheme.values().stream().filter(themeProgress -> themeProgress.rewardedTo(explorer))
            .map(ThemeProgress::themeId).toList();
    }

    public ThemeProgress progressOf(String themeId) {
        return progressByTheme.getOrDefault(themeId, ThemeProgress.empty(themeId));
    }

    public int completedCount() {
        return (int) progressByTheme.values().stream().filter(ThemeProgress::completed).count();
    }

    public String mapId() { return mapId; }

    /** 저장용: 행으로 쓸 테마 진행들(불변 사본). */
    public List<ThemeProgress> themeProgresses() { return List.copyOf(progressByTheme.values()); }
}
