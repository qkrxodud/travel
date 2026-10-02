package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 영토 애그리거트 — 탐험 컨텍스트의 진실 원천. 키는 explorerId가 아니라 mapId(공유 지도 단위).
 * 방문은 일급 컬렉션 {@link Visits}로 가지며 (지역, 멤버)당 하나다.
 *
 * 불변식
 * - 같은 (지역, 멤버)는 한 번만 방문 상태. 취소는 방문 상태에서만.
 * - 방문일은 과거 허용, 오늘 이후 거부.
 * - 하루 상한은 지도별·멤버별(CheckInPolicy.dailyCap). 지도 가입 후 onboardingGrace 동안은 미적용.
 * - photoRequired 지도에서는 사진 없는 체크인 거부.
 * - 폐지된 지역(retired)에는 새로 체크인할 수 없다(기존 방문은 유지).
 * - (지역, 멤버)마다 체크인 회차(generation)는 1부터 늘어난다(취소 후 다시 칠하면 +1, 결정 6).
 * - 선점은 보이는 방문 중 가장 먼저 칠한 멤버. 선점자의 방문이 취소·탈퇴로 빠지면 다음 멤버에게 넘어간다(§5).
 * - 탈퇴한 멤버의 방문은 숨긴다(유예). 유예 안 재가입이면 복구하되 넘어간 선점은 돌아오지 않는다(§2-9).
 */
public final class Territory {

    private final MapId mapId;
    private final Visits visits;
    private final VisitGenerations generations;

    private Territory(MapId mapId, Visits visits, VisitGenerations generations) {
        this.mapId = Objects.requireNonNull(mapId, "mapId");
        this.visits = visits;
        this.generations = generations;
    }

    public static Territory empty(MapId mapId) {
        return new Territory(mapId, Visits.empty(), VisitGenerations.empty());
    }

    /** 저장소에서 복원. 복원 데이터도 (지역, 멤버) 유일성을 지켜야 한다. 회차 기록이 없으면 방문의 회차로 채운다. */
    public static Territory restore(MapId mapId, Collection<Visit> visits) {
        return restore(mapId, visits, List.of());
    }

    public static Territory restore(MapId mapId, Collection<Visit> visits, Collection<VisitGenerations.VisitGeneration> generations) {
        return new Territory(mapId, Visits.of(visits), VisitGenerations.restore(generations, visits));
    }

    /** 지금 체크인한다면 성립하는 사실. 미리보기와 실제 체크인이 같은 계산을 쓴다. */
    public VisitFacts factsFor(ExplorerId member, RegionSnapshot region) {
        boolean already = visits.contains(region.code(), member);
        Visits mine = visits.of(member);
        int nth = already ? mine.size() : mine.size() + 1;
        return new VisitFacts(already, nth, !already && !mine.touches(region.provinceCode()), !visits.anyIn(region.code()));
    }

    public CheckInResult checkIn(ExplorerId member, RegionSnapshot region, VisitDate date, Memo memo, PhotoRef photo,
                                 CheckInContext ctx) {
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(date, "date");
        if (region.retired()) {
            throw ExplorationError.REGION_RETIRED.exception(region.code().value());
        }
        VisitFacts facts = factsFor(member, region);
        if (facts.alreadyVisited()) {
            throw ExplorationError.DUPLICATE_VISIT.exception(region.code().value());
        }
        requireNotFuture(date, ctx);
        requirePhoto(photo, ctx);
        if (!ctx.inOnboarding() && checkInsOn(member, ctx) >= ctx.policy().dailyCap()) {
            throw ExplorationError.DAILY_CAP_EXCEEDED.exception(ctx.policy().dailyCap());
        }
        Visit visit = Visit.checkedIn(region, member, date, memo, photo, ctx.now(), generations.next(region.code(), member));
        visits.add(visit);
        return new CheckInResult(mapId, visit, facts);
    }

    /** 부분 수정: patch 에서 생략한 항목은 기존 값을 유지한다. */
    public Visit editVisit(ExplorerId member, RegionCode code, VisitPatch patch, CheckInContext ctx) {
        Visit visit = visits.require(code, member);
        VisitDate date = patch.dateOr(visit.visitDate());
        PhotoRef photo = patch.photoOr(visit.photo());
        requireNotFuture(date, ctx);
        // 사진 필수는 새 체크인에만(Q5) — 사진 없던 기존 방문의 메모·날짜 수정은 허용, 있던 사진을 지우는 것만 막는다
        if (visit.photo() != null) requirePhoto(photo, ctx);
        visit.edit(date, patch.memoOr(visit.memo()), photo);
        return visit;
    }

    /** 체크인 취소(물리 삭제). 선점자의 방문이었고 다른 멤버 방문이 남아 있으면 선점이 그 멤버에게 넘어간다. */
    public CancelResult cancelVisit(ExplorerId member, RegionCode code) {
        Visit visit = visits.require(code, member);
        boolean wasClaim = visits.claimOf(code).orElseThrow() == visit;
        visits.remove(visit);
        ClaimTransfer transfer = wasClaim
            ? visits.claimOf(code).map(next -> new ClaimTransfer(visit.region(), member, next.checkedInBy(),
                ClaimTransfer.Reason.CANCELLED)).orElse(null)
            : null;
        return new CancelResult(mapId, visit, wasClaim, visits.of(member).size(), visits.anyIn(code), transfer);
    }

    /**
     * 탈퇴 유예 시작: 이 멤버가 탈퇴 시각(leftAt)까지 칠한 보이는 방문을 숨긴다. 그가 선점자였던 지역은 다음 순서의 멤버에게
     * 선점이 넘어간다. 이미 숨긴 상태면(재전달) 아무것도 하지 않는다(멱등). 탈퇴 처리가 늦게 와서 그사이 재가입 후 새로 칠한 방문은
     * 숨기지 않는다(QA P3-3).
     */
    public HideResult hideMember(ExplorerId member, Instant leftAt) {
        List<Visit> mine = visits.of(member, leftAt);
        List<Visit> claimed = visits.claimsAmong(mine);
        mine.forEach(visit -> visit.hide(leftAt));
        List<ClaimTransfer> transfers = claimed.stream()
            .flatMap(visit -> visits.claimOf(visit.regionCode()).stream()
                .map(next -> new ClaimTransfer(visit.region(), member, next.checkedInBy(), ClaimTransfer.Reason.LEFT)))
            .toList();
        List<RegionCode> gone = visits.regionsGoneAmong(mine);
        return new HideResult(mapId, member, mine.stream().map(Visit::regionCode).toList(), gone, transfers);
    }

    /** 유예 안 재가입: 숨긴 방문을 되살린다. 선점 순서는 복구 시각이라 넘어간 선점은 돌아오지 않는다. 멱등. */
    public RestoreResult restoreMember(ExplorerId member, Instant at) {
        List<RegionCode> restored = new ArrayList<>();
        List<RegionCode> back = new ArrayList<>();
        for (Visit visit : visits.hiddenOf(member)) {
            if (!visits.anyIn(visit.regionCode())) back.add(visit.regionCode());
            visit.restoreAt(at);
            restored.add(visit.regionCode());
        }
        return new RestoreResult(mapId, member, restored, back);
    }

    /** 유예가 끝남: 이 멤버의 숨긴 방문을 지운다(하드 삭제). 회차 기록은 남긴다. @return 지운 방문 */
    public List<Visit> purgeHidden(ExplorerId member) {
        List<Visit> hidden = visits.hiddenOf(member);
        hidden.forEach(visits::remove);
        return hidden;
    }

    /** 지도장의 이의 표시/해제(권한 확인은 ExpeditionMap). 개인 영토·전체 랭킹에는 영향 없음. */
    public Visit dispute(RegionCode code, ExplorerId member, boolean disputed) {
        Visit visit = visits.require(code, member);
        visit.dispute(disputed);
        return visit;
    }

    /**
     * 현재 방문을 처리 시각(visitedAt) 순으로 다시 체크인했을 때의 결과(사실 값 재계산). 진행·도감·퀘스트 재계산 배치
     * (RecalculateService)가 RegionVisited 를 재생할 때 쓴다. 취소된 방문은 물리 삭제돼 포함되지 않는다.
     */
    public List<CheckInResult> history() {
        Territory replay = empty(mapId);
        return visits.chronological().stream().map(visit -> {
            VisitFacts replayed = replay.factsFor(visit.checkedInBy(), visit.region());
            replay.visits.add(visit);
            // 선점은 처리 시각 순서가 아니라 지금의 선점 순서(claimRankAt)로 — 재가입 복구 뒤에도 실제 선점자와 같게(QA P3-2)
            boolean claim = visits.claimOf(visit.regionCode()).orElseThrow() == visit;
            return new CheckInResult(mapId, visit, new VisitFacts(replayed.alreadyVisited(), replayed.nth(),
                replayed.firstInProvince(), claim));
        }).toList();
    }

    /** 오늘(ctx.zone 기준) 이 멤버가 처리한 체크인 수. 취소된 방문은 물리 삭제되므로 세지 않는다. */
    public int checkInsOn(ExplorerId member, CheckInContext ctx) {
        return visits.of(member).processedOn(ctx.today(), ctx.zone());
    }

    /** 지도 정복률(전국·시·도별). provinceTotals: 시·도 코드 → 전체 지역 수(표시 순서). */
    public ConquestRate conquest(Map<String, Integer> provinceTotals) {
        return ConquestRate.of(visits.regions(), provinceTotals);
    }

    /** 지역의 선점 방문(지도 내 최초 체크인). */
    public Optional<Visit> claimOf(RegionCode code) {
        return visits.claimOf(code);
    }

    public Optional<Visit> find(RegionCode code, ExplorerId member) {
        return visits.find(code, member);
    }

    /** 이 멤버의 보이는 방문. */
    public List<Visit> visitsOf(ExplorerId member) {
        return visits.of(member).asList();
    }

    /** 지역마다 선점 방문(지도 색칠 — 지역 색은 선점자 색). */
    public List<Visit> claims() {
        return visits.claims();
    }

    /** 이 멤버가 선점자인 지역 수. */
    public int claimCountOf(ExplorerId member) {
        return visits.claimCountOf(member);
    }

    /** 지도장이 이의 표시한 보이는 방문. */
    public List<Visit> disputedVisits() {
        return visits.disputed();
    }

    /**
     * viewer 가 보는 이 지도의 방문(방문일 최근 순). 다른 멤버 방문의 메모·사진은 비운다(§7 프라이버시 — 색칠과 집계만).
     * 공개 경로(웹·Query)는 이것을 거쳐 방문을 내보낸다(QA P3-10).
     */
    public List<VisitView> viewedBy(ExplorerId viewer) {
        List<Visit> claims = visits.claims();
        return visits.recentFirst().stream().map(visit -> VisitView.of(visit, viewer, claims.contains(visit))).toList();
    }

    /** viewer 가 보는 방문 한 건(이 지도의 선점 여부 포함). */
    public VisitView viewOf(Visit visit, ExplorerId viewer) {
        return VisitView.of(visit, viewer, visits.claimOf(visit.regionCode()).filter(claim -> claim == visit).isPresent());
    }

    /** 지도에 칠해진 지역(멤버 무관, 중복 제거). */
    public Set<RegionSnapshot> claimedRegions() {
        return visits.regions();
    }

    /** 방문일 최근 순(탐험 일지). */
    public List<Visit> visitsRecentFirst() {
        return visits.recentFirst();
    }

    /** 보이는 방문. */
    public List<Visit> visits() {
        return visits.asList();
    }

    /** 숨긴 방문까지 전부(저장용). */
    public List<Visit> allVisits() {
        return visits.all();
    }

    /** 복원 이후 바뀐 회차(저장용). */
    public List<VisitGenerations.VisitGeneration> changedGenerations() {
        return generations.changed();
    }

    public MapId mapId() {
        return mapId;
    }

    private static void requireNotFuture(VisitDate date, CheckInContext ctx) {
        if (date.isAfter(ctx.today())) {
            throw ExplorationError.FUTURE_VISIT_DATE.exception(ctx.today());
        }
    }

    private static void requirePhoto(PhotoRef photo, CheckInContext ctx) {
        if (ctx.policy().photoRequired() && photo == null) {
            throw ExplorationError.PHOTO_REQUIRED.exception();
        }
    }
}
