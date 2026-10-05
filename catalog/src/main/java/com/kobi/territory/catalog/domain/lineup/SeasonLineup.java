package com.kobi.territory.catalog.domain.lineup;

import com.kobi.territory.catalog.domain.CatalogError;
import com.kobi.territory.catalog.domain.definition.SeasonRoundWindow;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 애그리거트 루트: 계절 회차 하나의 지역 목록(13s단계). 후보(마지막으로 모은 것, 미리보기)와 확정본(회차가 열리면 쓰는 것)을 따로 든다.
 * <ul>
 *   <li><b>회차가 열리면 고정</b> — 시작 시각부터 모으기·확정을 거절한다(SEASON_ROUND_LOCKED). 진행 중 지역이 바뀌면 진행도가 깨진다. 갱신은
 *       다음 회차부터</li>
 *   <li>확정본이 없으면 계절 정의의 기본 목록(AI 추정)을 쓴다 — 키가 없을 때의 지금 동작 그대로</li>
 *   <li>모으기에 실패하면 후보·확정본은 그대로 두고 마지막 시도에 까닭을 남긴다(관리자 경고)</li>
 *   <li>관리자가 확정한 회차는 자동 수집이 다시 모으거나 덮지 않는다. 관리자는 다시 모아 확정할 수 있다(회차 시작 전까지)</li>
 * </ul>
 */
public final class SeasonLineup {

    private final SeasonRoundWindow window;
    private LineupSnapshot candidate;
    private LineupSnapshot confirmed;
    private ConfirmedBy confirmedBy;
    private Instant confirmedAt;
    private CollectionAttempt lastAttempt;
    private final long version;

    private SeasonLineup(SeasonRoundWindow window, LineupSnapshot candidate, LineupSnapshot confirmed, ConfirmedBy confirmedBy,
                         Instant confirmedAt, CollectionAttempt lastAttempt, long version) {
        this.window = Objects.requireNonNull(window, "window");
        this.candidate = candidate;
        this.confirmed = confirmed;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt;
        this.lastAttempt = lastAttempt;
        this.version = version;
        if ((confirmed == null) != (confirmedBy == null) || (confirmed == null) != (confirmedAt == null)) {
            throw new IllegalArgumentException("확정본·확정한 쪽·확정 시각은 함께 있거나 함께 없다: " + window.roundId());
        }
    }

    private static final long NEW = -1;

    /** 아직 아무 기록도 없는 회차(저장소에 없음). */
    public static SeasonLineup start(SeasonRoundWindow window) {
        return new SeasonLineup(window, null, null, null, null, null, NEW);
    }

    public static SeasonLineup restore(SeasonRoundWindow window, LineupSnapshot candidate, LineupSnapshot confirmed,
                                       ConfirmedBy confirmedBy, Instant confirmedAt, CollectionAttempt lastAttempt, long version) {
        return new SeasonLineup(window, candidate, confirmed, confirmedBy, confirmedAt, lastAttempt, version);
    }

    /** 회차가 열리기 전에만 바꿀 수 있다. */
    public void requireChangeableAt(Instant at) {
        if (window.startedBy(at)) {
            throw CatalogError.SEASON_ROUND_LOCKED.exception(window.roundId());
        }
    }

    /** 회차가 열렸는데 아직 확정본이 없어 스냅숏으로 고정해야 하는지. */
    public boolean awaitingSnapshotAt(Instant at) {
        return window.startedBy(at) && confirmed == null;
    }

    /** 이 시각에 지역 목록이 고정됐는지(회차가 열렸거나 지났음). */
    public boolean lockedAt(Instant at) {
        return window.startedBy(at);
    }

    /**
     * 자동 수집이 지금 할 일. 열린 회차·관리자 확정 회차는 손대지 않는다. 같은 회차는 하루(서비스 시간대 달력 날짜)에 한 번
     * 넘게 모으지 않는다(일일 호출 예산 — 키가 없어 부르지 않은 시도는 세지 않는다: 키를 넣고 재기동하면 같은 날이라도 바로 모은다). 수집 기간(시작 leadDays 일 전부터) 안이면 처음이거나, 마지막 시도가 수집 기간 전이었거나, 실패한 뒤 하루가
     * 지났거나, 다시 모을 간격이 지났을 때 모은다. 수집 기간 전에는 한 번도 모으지 못했을 때만(처음 또는 실패 뒤 하루) 미리보기를 모은다.
     */
    public CollectionPlan planAt(Instant at, CollectionSchedule schedule) {
        if (window.startedBy(at) || confirmedBy == ConfirmedBy.ADMIN) return CollectionPlan.NONE;
        boolean calledToday = lastAttempt != null && lastAttempt.outcome() != CollectionAttempt.Outcome.NOT_CONFIGURED
            && schedule.sameDayOrLater(lastAttempt.at(), at);
        if (calledToday) return CollectionPlan.NONE;
        Instant opensAt = window.collectionOpensAt(schedule.leadDays());
        boolean neverCollected = lastAttempt == null || lastAttempt.failed() && candidate == null;
        if (at.isBefore(opensAt)) return neverCollected ? CollectionPlan.PREVIEW : CollectionPlan.NONE;
        boolean due = lastAttempt == null || lastAttempt.failed() || lastAttempt.at().isBefore(opensAt)
            || schedule.recollectDue(lastAttempt.at(), at);
        if (!due) return CollectionPlan.NONE;
        return schedule.autoConfirm() ? CollectionPlan.COLLECT_AND_CONFIRM : CollectionPlan.COLLECT;
    }

    /** 모은 결과를 남긴다 — 읽었으면 새 후보, 못 읽었으면 후보·확정본은 그대로 두고 까닭만. */
    public void record(FestivalFetch fetch, LineupSelector selector, Instant at) {
        requireChangeableAt(at);
        switch (fetch) {
            case FestivalFetch.Fetched fetched -> {
                LineupSelection selection = selector.select(window, fetched);
                candidate = new LineupSnapshot(selection.regions(), fetched.fetchedAt(), selection.warnings());
                boolean full = selection.evidencedRegions() >= selection.regions().size();
                lastAttempt = new CollectionAttempt(at, full ? CollectionAttempt.Outcome.COLLECTED : CollectionAttempt.Outcome.PARTIAL,
                    selection.warnings());
            }
            case FestivalFetch.Failed failed -> {
                List<String> warnings = new ArrayList<>();
                warnings.add(failed.message());
                warnings.add(confirmed == null ? "지금은 AI 추정 목록을 그대로 씁니다" : "지금 확정된 목록을 그대로 씁니다");
                lastAttempt = new CollectionAttempt(at, CollectionAttempt.Outcome.of(failed.failure()), warnings);
            }
        }
    }

    /** 후보를 확정한다(회차 시작 전, 후보가 있어야). 확정본이 후보로 바뀌고 후보는 비운다. */
    public void confirm(ConfirmedBy by, Instant at) {
        Objects.requireNonNull(by, "by");
        requireChangeableAt(at);
        if (candidate == null) throw CatalogError.SEASON_CANDIDATE_MISSING.exception(window.roundId());
        confirmed = candidate;
        candidate = null;
        confirmedBy = by;
        confirmedAt = at;
    }

    /**
     * 자동 수집 뒤: 계획이 확정까지이고 후보의 TourAPI 근거 지역이 최소 수 이상이면 자동 확정한다. 모자라면 후보로만 두고 지금 목록을 유지한다.
     *
     * @return 확정했는지
     */
    public boolean confirmAutomatically(CollectionPlan plan, CollectionSchedule schedule, Instant at) {
        if (plan != CollectionPlan.COLLECT_AND_CONFIRM || candidate == null) return false;
        if (candidate.regions().count(LineupProvenance.TOURAPI) < schedule.autoConfirmMinRegions()) return false;
        confirm(ConfirmedBy.AUTO, at);
        return true;
    }

    /**
     * 회차가 열렸는데 확정본이 없으면 지금 쓰는 기본 목록(AI 추정)을 확정본으로 고정한다(스냅숏) — 그 뒤 계절 정의의 기본 목록이 바뀌어도 진행
     * 중·지난 회차는 바뀌지 않는다. 확정 전 후보는 버린다(회차에 쓰이지 않았다). 열리기 전이거나 이미 확정본이 있으면 아무것도 안 한다.
     *
     * @return 고정했는지
     */
    public boolean freezeOpened(List<RegionCode> defaultRegions, Instant at) {
        if (!window.startedBy(at) || confirmed != null) return false;
        confirmed = new LineupSnapshot(LineupRegions.aiEstimate(defaultRegions), at,
            List.of("확정 없이 회차가 열려 그때의 기본 목록(AI 추정)으로 고정했습니다"));
        candidate = null;
        confirmedBy = ConfirmedBy.OPENING;
        confirmedAt = at;
        return true;
    }

    /** 이 회차에 쓰는(쓸) 지역 목록 — 확정본, 없으면 기본 목록(AI 추정). */
    public LineupRegions inEffect(List<RegionCode> defaultRegions) {
        return confirmed != null ? confirmed.regions() : LineupRegions.aiEstimate(defaultRegions);
    }

    /** 관리자에게 먼저 보일 경고: 마지막 시도가 실패했으면 그 까닭, 아니면 확정 전 후보의 경고(부족분 채움 등). */
    public List<String> alerts() {
        if (lastAttempt != null && lastAttempt.failed()) return lastAttempt.warnings();
        return candidate == null ? List.of() : candidate.warnings();
    }

    public SeasonRoundWindow window() {
        return window;
    }

    public String roundId() {
        return window.roundId();
    }

    public LineupSnapshot candidate() {
        return candidate;
    }

    public LineupSnapshot confirmed() {
        return confirmed;
    }

    public ConfirmedBy confirmedBy() {
        return confirmedBy;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public CollectionAttempt lastAttempt() {
        return lastAttempt;
    }

    /** 저장된 버전(낙관적 잠금). 아직 저장되지 않았으면 음수. */
    public long version() {
        return version;
    }

    /** 아직 저장소에 없는 새 회차 기록인지. */
    public boolean isNew() {
        return version == NEW;
    }
}
