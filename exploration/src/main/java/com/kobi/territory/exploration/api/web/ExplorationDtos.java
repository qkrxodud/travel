package com.kobi.territory.exploration.api.web;

import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RegionView;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.exploration.application.CheckInService.PreviewOutcome;
import com.kobi.territory.exploration.domain.territory.CheckInPreview;
import com.kobi.territory.exploration.domain.territory.VisitView;
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

    /** @param accessToken 비밀 접근 토큰(X-Explorer-Token) — 발급(POST /explorers) 응답에만 실린다. 그 밖에는 null */
    /** @param handle 계정 연결된 탐험가의 공개 handle(4단계, 익명은 null) */
    public record ExplorerResponse(String explorerId, String personalMapId, boolean anonymous, Instant createdAt,
                                   String accessToken, String handle) {}

    /** PUT /me/handle */
    public record HandleRequest(String handle) {}

    public record HandleResponse(String explorerId, String handle) {}

    /** @param mapId 생략하면 개인 지도 */
    public record CheckInRequest(@NotBlank String regionCode, @NotNull LocalDate visitDate, String memo, String photoUrl,
                                 String mapId) {}

    /** 부분 수정: 생략(null) 필드는 유지. 메모를 지우려면 "". */
    public record EditVisitRequest(LocalDate visitDate, String memo, String photoUrl) {}

    /**
     * 방문 한 건. 공유 지도에서 다른 멤버의 방문은 메모·사진을 비운다(§7 프라이버시 — 색칠과 집계만).
     *
     * @param claim    이 방문이 그 지역의 선점(지역 색 = 선점자 색)인지
     * @param disputed 지도장 이의 표시(지도 내 랭킹 집계 제외)
     * @param generation 같은 (지도, 지역, 멤버)의 체크인 회차
     */
    public record VisitResponse(String regionCode, String regionName, String provinceCode, String provinceName,
                                Rarity rarity, LocalDate visitDate, String memo, String photoUrl, String checkedInBy,
                                String verification, Instant visitedAt, boolean claim, boolean disputed, int generation) {

        /** 도메인이 정한 보이는 값(VisitView — 다른 멤버 메모·사진은 이미 비워짐)을 옮기기만 한다. */
        static VisitResponse of(VisitView view, RegionCatalog catalog) {
            RegionView region = catalog.findRegion(view.region().code()).orElseThrow();
            return new VisitResponse(region.code(), region.name(), region.provinceCode(), region.provinceName(), region.rarity(),
                view.visitDate().value(), view.memo().value(), view.photo() == null ? null : view.photo().url(),
                view.checkedInBy().value(), view.verification().name(), view.visitedAt(), view.claim(), view.disputed(),
                view.generation());
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

    /** @param claims 지역마다 선점자(지역 코드 → explorerId) — 공유 지도 색칠용 */
    public record TerritoryResponse(String mapId, String mapName, String mapKind, ConquestResponse conquest,
                                    List<ProvinceConquestResponse> provinces, List<VisitResponse> visits,
                                    List<ClaimResponse> claims) {}

    public record ClaimResponse(String regionCode, String explorerId) {}
}
