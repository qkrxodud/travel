package com.kobi.territory.exploration.api.web;

import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RegionView;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.revisit.RevisitStamp;
import com.kobi.territory.exploration.domain.revisit.StampBook;
import com.kobi.territory.exploration.domain.revisit.StampEligibility;
import com.kobi.territory.exploration.domain.revisit.StampRefusal;
import com.kobi.territory.exploration.domain.revisit.StampResult;
import java.time.Instant;
import java.util.List;

/** 재방문 도장 API 응답 DTO(9단계). */
public final class RevisitDtos {

    private RevisitDtos() {}

    /**
     * POST /revisits/{code} 201.
     *
     * @param xp         이 도장으로 받는 XP(진행이 비동기로 반영 — 카탈로그 값)
     * @param stampCount 이 도장을 포함한 내 도장 수
     */
    public record StampResponse(String regionCode, String regionName, int year, int firstYear, Instant stampedAt, int xp,
                                int stampCount) {
        static StampResponse of(StampResult result, RegionCatalog catalog, int xp) {
            RevisitStamp stamp = result.stamp();
            return new StampResponse(stamp.region().value(), nameOf(catalog, stamp.region()), stamp.year(), result.firstYear(),
                stamp.stampedAt(), xp, result.stampCount());
        }
    }

    public record StampItem(String regionCode, String regionName, String provinceCode, int year, Instant stampedAt) {}

    /** GET /revisits — 받은 순서의 반대(최근 먼저). */
    public record StampBookResponse(int count, int xpPerStamp, List<StampItem> stamps) {
        static StampBookResponse of(StampBook stampBook, RegionCatalog catalog, int xpPerStamp) {
            return new StampBookResponse(stampBook.stamps().count(), xpPerStamp, stampBook.stamps().newestFirst().stream()
                .map(stamp -> {
                    RegionView region = catalog.findRegion(stamp.region()).orElse(null);
                    return new StampItem(stamp.region().value(), region == null ? null : region.name(),
                        region == null ? null : region.provinceCode(), stamp.year(), stamp.stampedAt());
                }).toList());
        }
    }

    /**
     * GET /revisits/{code} — 지금 도장을 받을 수 있는지(지역 상세의 "다시 다녀왔어요" 버튼 안내).
     *
     * @param painted           탐험가 단위로 칠한 지역인지
     * @param firstYear         처음 칠한 해(칠하지 않았으면 null)
     * @param year              지금 연도(처리 시각 기준)
     * @param stampedYears      이 지역에서 받은 도장 연도(오름차순)
     * @param canStamp          지금 누르면 받는지
     * @param reason            못 받는 이유 NOT_PAINTED | SAME_YEAR | ALREADY_STAMPED | DAILY_CAP, 받을 수 있으면 null
     * @param availableFromYear SAME_YEAR·ALREADY_STAMPED 일 때 받을 수 있게 되는 해, 그 밖은 null
     */
    public record StampStatusResponse(String regionCode, String regionName, boolean painted, Integer firstYear, int year,
                                      List<Integer> stampedYears, boolean canStamp, String reason, Integer availableFromYear,
                                      int xp) {
        static StampStatusResponse of(StampEligibility eligibility, RegionCatalog catalog, int xp) {
            return new StampStatusResponse(eligibility.region().value(), nameOf(catalog, eligibility.region()),
                eligibility.firstYear().isPresent(), eligibility.firstYear().orElse(null), eligibility.year(),
                eligibility.stampedYears(), eligibility.stampable(), eligibility.refusal().map(StampRefusal::name).orElse(null),
                eligibility.availableFromYear().orElse(null), xp);
        }
    }

    static String nameOf(RegionCatalog catalog, RegionCode code) {
        return catalog.findRegion(code).map(RegionView::name).orElse(null);
    }
}
