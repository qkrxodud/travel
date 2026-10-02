package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
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
 */
public final class Territory {

    private final MapId mapId;
    private final Visits visits;

    private Territory(MapId mapId, Visits visits) {
        this.mapId = Objects.requireNonNull(mapId, "mapId");
        this.visits = visits;
    }

    public static Territory empty(MapId mapId) {
        return new Territory(mapId, Visits.empty());
    }

    /** 저장소에서 복원. 복원 데이터도 (지역, 멤버) 유일성을 지켜야 한다. */
    public static Territory restore(MapId mapId, Collection<Visit> visits) {
        return new Territory(mapId, Visits.of(visits));
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
        Visit visit = new Visit(region, member, date, memo, photo, Verification.NONE, ctx.now());
        visits.add(visit);
        return new CheckInResult(mapId, visit, facts);
    }

    /** 부분 수정: patch 에서 생략한 항목은 기존 값을 유지한다. */
    public Visit editVisit(ExplorerId member, RegionCode code, VisitPatch patch, CheckInContext ctx) {
        Visit visit = visits.require(code, member);
        VisitDate date = patch.dateOr(visit.visitDate());
        PhotoRef photo = patch.photoOr(visit.photo());
        requireNotFuture(date, ctx);
        requirePhoto(photo, ctx);
        visit.edit(date, patch.memoOr(visit.memo()), photo);
        return visit;
    }

    public CancelResult cancelVisit(ExplorerId member, RegionCode code) {
        Visit visit = visits.require(code, member);
        boolean wasClaim = visits.claimOf(code).orElseThrow() == visit;
        visits.remove(visit);
        return new CancelResult(mapId, visit, wasClaim, visits.of(member).size());
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

    public List<Visit> visitsOf(ExplorerId member) {
        return visits.of(member).asList();
    }

    /** 지도에 칠해진 지역(멤버 무관, 중복 제거). */
    public Set<RegionSnapshot> claimedRegions() {
        return visits.regions();
    }

    /** 방문일 최근 순(탐험 일지). */
    public List<Visit> visitsRecentFirst() {
        return visits.recentFirst();
    }

    public List<Visit> visits() {
        return visits.asList();
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
