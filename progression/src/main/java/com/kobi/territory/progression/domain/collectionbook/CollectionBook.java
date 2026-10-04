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
 * 9단계 계절 한정 테마(season_progress — 테마와 같은 지도 단위 진행·완성 시점 멤버 전원 수령, 판정 규칙만 다르다)
 * - 그 회차 기간 안에 처리된 체크인만 센다(기간 전 방문·소급 없음). 그래서 진행은 "지도에 칠해진 지역"이 아니라 기간 안에 센 방문
 *   (지역, 멤버)의 지역이다 — 기간 전에 칠해 둔 지역은 기간 안에 누군가 다시 칠해야 들어간다.
 * - 기간 안의 취소·탈퇴 숨김은 그 방문의 표시만 뺀다(같은 지역을 기간 안에 칠한 다른 멤버가 있으면 진행에 남는다). 재가입 복구는 원래 처리
 *   시각이 기간 안인 방문만 다시 센다.
 * - 기간이 끝나면 회차는 닫힌다 — 미완성 진행도 기록으로 남고 더는 바뀌지 않는다. 다음 해는 새 회차로 0부터.
 * - 완성은 회차당 한 번(취소해도 완성 기록 유지).
 */
public final class CollectionBook {

    private final String mapId;
    private final Map<String, ThemeProgress> progressByTheme;
    private final Map<String, SeasonProgress> progressByRound;

    private CollectionBook(String mapId, Collection<ThemeProgress> restored, Collection<SeasonProgress> restoredSeasons) {
        this.mapId = Objects.requireNonNull(mapId, "mapId");
        this.progressByTheme = new LinkedHashMap<>();
        this.progressByRound = new LinkedHashMap<>();
        restored.forEach(themeProgress -> progressByTheme.put(themeProgress.themeId(), themeProgress));
        restoredSeasons.forEach(seasonProgress -> progressByRound.put(seasonProgress.roundId(), seasonProgress));
    }

    public static CollectionBook empty(String mapId) {
        return new CollectionBook(mapId, List.of(), List.of());
    }

    public static CollectionBook restore(String mapId, Collection<ThemeProgress> restored) {
        return new CollectionBook(mapId, restored, List.of());
    }

    public static CollectionBook restore(String mapId, Collection<ThemeProgress> restored, Collection<SeasonProgress> seasons) {
        return new CollectionBook(mapId, restored, seasons);
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

    /** 재계산용: 완성 기록(completedAt)은 남기고 모은 지역만 비운 도감 — 완성은 한 번이고 취소해도 유지되는 불변식 때문. 계절 진행은 그대로. */
    public CollectionBook rebuildBase() {
        return new CollectionBook(mapId, progressByTheme.values().stream().map(ThemeProgress::withoutCollected).toList(),
            progressByRound.values());
    }

    /**
     * 재계산용(9단계): 테마는 {@link #rebuildBase()} 와 같고, 계절은 at 에 아직 닫히지 않은 회차만 센 방문을 비운다(완성 기록은 유지).
     * 닫힌 회차는 확정 기록이라 그대로 둔다 — 재생은 {@link SeasonCalendar#excludingEndedBy} 로 닫힌 회차를 건드리지 않는다.
     */
    public CollectionBook rebuildBase(SeasonCalendar calendar, Instant at) {
        return new CollectionBook(mapId, progressByTheme.values().stream().map(ThemeProgress::withoutCollected).toList(),
            progressByRound.values().stream().map(seasonProgress -> calendar.round(seasonProgress.roundId())
                .filter(round -> !round.endedBy(at)).map(round -> seasonProgress.withoutMarks()).orElse(seasonProgress)).toList());
    }

    // ---- 계절 한정 테마(9단계) ----------------------------------------------------------------------------------

    /**
     * 체크인(처리 시각 at) → 그 시각에 열린 회차 중 이 지역을 포함하는 회차에 방문을 센다. 처음 완성된 회차를 돌려준다. 멱등.
     *
     * @param members 칠한 시점의 지도 멤버(완성 보상 수령자). 비었으면 칠한 사람만
     */
    public List<SeasonCompletion> applySeasonVisit(RegionCode region, ExplorerId visitor, Instant at, SeasonCalendar calendar,
                                                   Collection<ExplorerId> members) {
        Set<ExplorerId> recipients = new LinkedHashSet<>(members);
        recipients.add(visitor);
        List<SeasonCompletion> completions = new ArrayList<>();
        for (SeasonRound round : calendar.roundsCovering(region, at)) {
            SeasonProgress next = seasonProgressOf(round.roundId()).with(new SeasonMark(region, visitor));
            if (!next.completed() && round.completedBy(next.collected())) {
                next = next.completedAt(at, recipients);
                completions.add(new SeasonCompletion(mapId, round.roundId(), visitor, at, List.copyOf(recipients)));
            }
            progressByRound.put(round.roundId(), next);
        }
        return List.copyOf(completions);
    }

    /** 취소(처리 시각 at) — 그 시각에 열린 회차에서 이 멤버의 그 지역 표시를 뺀다(닫힌 회차는 그대로). 완성 기록은 유지. 멱등. */
    public void revokeSeasonVisit(RegionCode region, ExplorerId member, Instant at, SeasonCalendar calendar) {
        SeasonMark mark = new SeasonMark(region, member);
        calendar.roundsCovering(region, at).forEach(round ->
            progressByRound.computeIfPresent(round.roundId(), (roundId, seasonProgress) -> seasonProgress.without(mark)));
    }

    /** 탈퇴 숨김(at) — 열린 회차에서 그 멤버가 숨겨진 지역들의 표시를 뺀다. 멱등. */
    public void revokeSeasonMember(ExplorerId member, Collection<RegionCode> hiddenRegions, Instant at, SeasonCalendar calendar) {
        List<SeasonMark> gone = hiddenRegions.stream().map(region -> new SeasonMark(region, member)).toList();
        calendar.roundsOpenAt(at).forEach(round ->
            progressByRound.computeIfPresent(round.roundId(), (roundId, seasonProgress) -> seasonProgress.withoutAll(gone)));
    }

    /**
     * 재가입 복구(at) — 열린 회차에 복구된 방문 중 원래 처리 시각이 그 회차 기간 안인 것만 다시 센다. 그로 인해 처음 완성된 회차를 돌려준다
     * (수령자 = 복구 시점 멤버).
     *
     * @param visitedAt 복구한 방문마다 원래 처리 시각
     */
    public List<SeasonCompletion> restoreSeasonVisits(Map<RegionCode, Instant> visitedAt, ExplorerId member, Instant at,
                                                      SeasonCalendar calendar, Collection<ExplorerId> members) {
        List<SeasonCompletion> completions = new ArrayList<>();
        for (SeasonRound round : calendar.roundsOpenAt(at)) {
            SeasonProgress next = seasonProgressOf(round.roundId());
            for (Map.Entry<RegionCode, Instant> restored : visitedAt.entrySet()) {
                if (round.includes(restored.getKey()) && round.openAt(restored.getValue())) {
                    next = next.with(new SeasonMark(restored.getKey(), member));
                }
            }
            if (!next.completed() && round.completedBy(next.collected())) {
                Set<ExplorerId> recipients = new LinkedHashSet<>(members);
                next = next.completedAt(at, recipients);
                completions.add(new SeasonCompletion(mapId, round.roundId(), member, at, List.copyOf(recipients)));
            }
            if (next.have() > 0 || next.completed()) progressByRound.put(round.roundId(), next);
        }
        return List.copyOf(completions);
    }

    /** 병합 재귀속(at) — 열린 회차에서 from 의 표시를 into 의 것으로 바꾼다(지도에 칠해진 것은 그대로라 진행 수는 같다). 멱등. */
    public void reassignSeasonMember(ExplorerId from, ExplorerId into, Instant at, SeasonCalendar calendar) {
        calendar.roundsOpenAt(at).forEach(round ->
            progressByRound.computeIfPresent(round.roundId(), (roundId, seasonProgress) -> seasonProgress.reassigned(from, into)));
    }

    public SeasonProgress seasonProgressOf(String roundId) {
        return progressByRound.getOrDefault(roundId, SeasonProgress.empty(roundId));
    }

    /** 이 탐험가가 완성 보상 수령자(완성 시점 멤버)인 회차 id — 재계산 복구 규칙의 기준. */
    public List<String> seasonRoundIdsRewardedTo(ExplorerId explorer) {
        return progressByRound.values().stream().filter(seasonProgress -> seasonProgress.rewardedTo(explorer))
            .map(SeasonProgress::roundId).toList();
    }

    /** 저장·조회용: 계절 회차 진행들(불변 사본). */
    public List<SeasonProgress> seasonProgresses() { return List.copyOf(progressByRound.values()); }

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
