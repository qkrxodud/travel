package com.kobi.territory.exploration.api;

import com.kobi.territory.catalog.api.ItemView;
import com.kobi.territory.catalog.api.RegionCatalog;
import com.kobi.territory.catalog.api.RegionView;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.exploration.application.CheckInService.PreviewOutcome;
import com.kobi.territory.exploration.domain.CheckInPreview;
import com.kobi.territory.exploration.domain.Visit;
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

        static VisitResponse of(Visit v, RegionCatalog catalog) {
            RegionView r = catalog.findRegion(v.regionCode()).orElseThrow();
            return new VisitResponse(r.code(), r.name(), r.provinceCode(), r.provinceName(), r.rarity(),
                v.visitDate().value(), v.memo().value(), v.photo() == null ? null : v.photo().url(),
                v.checkedInBy().value(), v.verification().name(), v.visitedAt());
        }
    }

    public record XpLineResponse(String source, String label, int amount) {}

    public record XpResponse(List<XpLineResponse> lines, int total) {}

    public record PreviewResponse(String mapId, String regionCode, String regionName, String provinceCode,
                                  String provinceName, Rarity rarity, boolean alreadyVisited, int nth,
                                  boolean firstInProvince, boolean firstClaim, XpResponse xp, List<ItemView> items) {

        static PreviewResponse of(PreviewOutcome o) {
            CheckInPreview.Result p = o.preview();
            RegionView r = o.region();
            List<XpLineResponse> lines = p.lines().stream()
                .map(l -> new XpLineResponse(l.source().name(), label(l.source(), r), l.amount())).toList();
            return new PreviewResponse(o.mapId().value(), r.code(), r.name(), r.provinceCode(), r.provinceName(),
                r.rarity(), p.facts().alreadyVisited(), p.facts().nth(), p.facts().firstInProvince(),
                p.facts().firstClaim(), new XpResponse(lines, p.totalXp()), o.items());
        }

        private static String label(CheckInPreview.XpSource source, RegionView r) {
            return switch (source) {
                case REGION_BASE -> RARITY_LABEL.get(r.rarity()) + " 지역 기본";
                case PROVINCE_FIRST -> r.provinceName() + " 첫 발 도장";
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
