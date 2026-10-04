package com.kobi.territory.exploration.domain.revisit;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.ExplorationException;
import com.kobi.territory.exploration.domain.territory.CheckInContext;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 재방문 도장첩 애그리거트(explorerId, 9단계). 설계 §7 "재방문 도장(같은 지역 2회차부터 카운트)".
 *
 * 불변식
 * - 이미 칠한 지역(탐험가 단위로 보이는 방문이 있음)에만 받는다.
 * - 그 지역을 처음 칠한 해보다 뒤의 해여야 한다(같은 해에 칠하고 바로 누르는 자기 신고 악용 방지). 날짜는 처리 시각 기준, 소급 없음.
 * - 지역·연도당 하나. 취소 개념이 없다(도장은 지우지 않는다). 영토·선점·정복률은 바뀌지 않는다.
 * - 하루 체크인 상한을 개인 지도 체크인과 함께 쓴다(도장도 1건 — 온보딩 예외도 같은 규칙).
 */
public final class StampBook {

    private final ExplorerId explorerId;
    private final RevisitStamps stamps;

    private StampBook(ExplorerId explorerId, RevisitStamps stamps) {
        this.explorerId = Objects.requireNonNull(explorerId, "explorerId");
        this.stamps = Objects.requireNonNull(stamps, "stamps");
    }

    public static StampBook empty(ExplorerId explorerId) {
        return new StampBook(explorerId, RevisitStamps.empty());
    }

    public static StampBook restore(ExplorerId explorerId, RevisitStamps stamps) {
        return new StampBook(explorerId, stamps);
    }

    /**
     * 지금 이 지역에 도장을 받을 수 있는지.
     *
     * @param firstPaintedAt 이 탐험가가 그 지역을 처음 칠한 처리 시각(남은 보이는 방문 중 가장 이른 것), 칠하지 않았으면 빈 값
     * @param checkInsToday  오늘 개인 지도에서 처리한 체크인 수(하루 상한을 함께 쓴다)
     * @param ctx            개인 지도 체크인 규칙(상한·온보딩)·처리 시각·시간대
     */
    public StampEligibility judge(RegionCode region, Optional<Instant> firstPaintedAt, int checkInsToday, CheckInContext ctx) {
        int year = yearOf(ctx.now(), ctx.zone());
        Optional<Integer> firstYear = firstPaintedAt.map(at -> yearOf(at, ctx.zone()));
        return new StampEligibility(region, year, firstYear, stamps.yearsOf(region),
            refusalOf(region, year, firstYear, checkInsToday, ctx));
    }

    /**
     * 도장을 받는다 — 받을 수 없으면 이유에 맞는 오류(REVISIT_NOT_PAINTED·REVISIT_SAME_YEAR·REVISIT_ALREADY_STAMPED·DAILY_CAP_EXCEEDED).
     * @return 새 도장과 처음 칠한 해·도장 수 */
    public StampResult stamp(RegionCode region, Optional<Instant> firstPaintedAt, int checkInsToday, CheckInContext ctx) {
        StampEligibility eligibility = judge(region, firstPaintedAt, checkInsToday, ctx);
        eligibility.refusal().ifPresent(refusal -> {
            throw refused(refusal, eligibility, ctx);
        });
        RevisitStamp stamp = new RevisitStamp(region, eligibility.year(), ctx.now());
        stamps.add(stamp);
        return new StampResult(stamp, eligibility.firstYear().orElseThrow(), stamps.count());
    }

    /**
     * 계정 병합(ExplorerMerged): 익명 탐험가의 도장을 이 도장첩으로 옮긴다 — 같은 (지역, 연도)는 하나만(이미 있으면 그대로). 하루 상한은
     * 입력 규칙이라 병합에는 적용하지 않는다. 멱등. @return 새로 옮겨 온 도장
     */
    public List<RevisitStamp> absorb(StampBook merged) {
        List<RevisitStamp> adopted = merged.stamps.newestFirst().reversed().stream()
            .filter(stamp -> !stamps.has(stamp.region(), stamp.year())).toList();
        adopted.forEach(stamps::add);
        return adopted;
    }

    private Optional<StampRefusal> refusalOf(RegionCode region, int year, Optional<Integer> firstYear, int checkInsToday,
                                             CheckInContext ctx) {
        if (firstYear.isEmpty()) return Optional.of(StampRefusal.NOT_PAINTED);
        if (year <= firstYear.get()) return Optional.of(StampRefusal.SAME_YEAR);
        if (stamps.has(region, year)) return Optional.of(StampRefusal.ALREADY_STAMPED);
        if (ctx.alsoUsing(stamps.countOn(ctx.today(), ctx.zone())).capReachedWith(checkInsToday)) {
            return Optional.of(StampRefusal.DAILY_CAP);
        }
        return Optional.empty();
    }

    private static ExplorationException refused(StampRefusal refusal, StampEligibility eligibility, CheckInContext ctx) {
        return switch (refusal) {
            case NOT_PAINTED -> ExplorationError.REVISIT_NOT_PAINTED.exception(eligibility.region().value());
            case SAME_YEAR -> ExplorationError.REVISIT_SAME_YEAR.exception(eligibility.year(), eligibility.year() + 1);
            case ALREADY_STAMPED -> ExplorationError.REVISIT_ALREADY_STAMPED.exception(eligibility.year());
            case DAILY_CAP -> ExplorationError.dailyCapWithStamps(ctx.policy().dailyCap());
        };
    }

    private static int yearOf(Instant at, ZoneId zone) {
        return at.atZone(zone).getYear();
    }

    public ExplorerId explorerId() { return explorerId; }
    public RevisitStamps stamps() { return stamps; }
}
