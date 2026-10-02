package com.kobi.territory.exploration.api.web;

import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RegionView;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.exploration.application.CheckInService.PreviewOutcome;
import com.kobi.territory.exploration.domain.territory.CheckInPreview;
import com.kobi.territory.exploration.domain.territory.Visit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 탐험 API 요청/응답 DTO. */
public final class ExplorationDtos {

    private ExplorationDtos() {}

    static final Map<Rarity, String> RARITY_LABEL = Map.of(Rarity.COMMON, "일반", Rarity.RARE, "희귀", Rarity.LEGEND, "전설");

    public record ExplorerResponse(String explorerId, String personalMapId, boolean anonymous, Instant createdAt) {}

    /** @param mapId 생략하면 개인 지도 */
    public record CheckInRequest(@NotBlank String regionCode, @NotNull LocalDate visitDate, String memo, String photoUrl,
                                 String mapId) {}

    /** 부분 수정: 생략(null) 필드는 유지. 메모를 지우려면 "". */
    public record EditVisitRequest(LocalDate visitDate, String memo, String photoUrl) {}

    public record VisitResponse(String regionCode, String regionName, String provinceCode, String provinceName,
                                Rarity rarity, LocalDate visitDate, String memo, String photoUrl, String checkedInBy,
                                String verification, Instant visitedAt) {

        static VisitResponse of(Visit visit, RegionCatalog catalog) {
            RegionView region = catalog.findRegion(visit.regionCode()).orElseThrow();
            return new VisitResponse(region.code(), region.name(), region.provinceCode(), region.provinceName(), region.rarity(),
                visit.visitDate().value(), visit.memo().value(), visit.photo() == null ? null : visit.photo().url(),
                visit.checkedInBy().value(), visit.verification().name(), visit.visitedAt());
        }
    }

    public record XpLineResponse(String source, String label, int amount) {}

    /**
     * 예상 XP. basis=MAP_MAX: 이 지도 기준 최대 보상(D1) — 기본 XP는 탐험가당 지역당 1회, 시·도 보너스는 탐험가당 1회라
     * 다른 지도에서 이미 받았거나 취소 후 다시 칠하면 실제 지급(GET /progress)은 이보다 적을 수 있다.
     */
    public record XpResponse(List<XpLineResponse> lines, int total, String basis, String note) {
        static final String BASIS = "MAP_MAX";
        static final String NOTE = "이 지도 기준 최대 보상이에요. 이미 받은 기본 XP·시·도 보너스·선점 보너스는 다시 지급되지 않아 실제 지급은 적을 수 있어요.";

        XpResponse(List<XpLineResponse> lines, int total) {
            this(lines, total, BASIS, NOTE);
        }
    }

    public record PreviewResponse(String mapId, String regionCode, String regionName, String provinceCode,
                                  String provinceName, Rarity rarity, boolean alreadyVisited, int nth,
                                  boolean firstInProvince, boolean firstClaim, XpResponse xp, List<ItemView> items) {

        static PreviewResponse of(PreviewOutcome outcome) {
            CheckInPreview.Result preview = outcome.preview();
            RegionView region = outcome.region();
            List<XpLineResponse> lines = preview.lines().stream()
                .map(line -> new XpLineResponse(line.source().name(), label(line.source(), region), line.amount())).toList();
            return new PreviewResponse(outcome.mapId().value(), region.code(), region.name(), region.provinceCode(), region.provinceName(),
                region.rarity(), preview.facts().alreadyVisited(), preview.facts().nth(), preview.facts().firstInProvince(),
                preview.facts().firstClaim(), new XpResponse(lines, preview.totalXp()), outcome.items());
        }

        private static String label(CheckInPreview.XpSource source, RegionView region) {
            return switch (source) {
                case REGION_BASE -> RARITY_LABEL.get(region.rarity()) + " 지역 기본";
                case PROVINCE_FIRST -> region.provinceName() + " 첫 발 도장";
                case FIRST_CLAIM -> "선점 보너스";
            };
        }
    }

    /** 체크인 응답: 영토 반영 + 받을 아이템 + 예상 XP(동기 계산분만). */
    public record CheckInResponse(String mapId, VisitResponse visit, int nth, boolean firstInProvince,
                                  boolean firstClaim, XpResponse xp, List<ItemView> items) {}

    public record ConquestResponse(int visited, int total, int percent) {}

    public record ProvinceConquestResponse(String code, String name, int visited, int total, int percent,
                                           boolean conquered) {}

    public record TerritoryResponse(String mapId, String mapName, String mapKind, ConquestResponse conquest,
                                    List<ProvinceConquestResponse> provinces, List<VisitResponse> visits) {}
}
