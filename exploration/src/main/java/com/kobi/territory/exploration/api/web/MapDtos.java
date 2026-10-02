package com.kobi.territory.exploration.api.web;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.application.MapService.MapView;
import com.kobi.territory.exploration.domain.map.ExpeditionMap;
import com.kobi.territory.exploration.domain.map.Member;
import com.kobi.territory.exploration.domain.territory.Territory;
import com.kobi.territory.exploration.domain.territory.Visit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** 공유 지도 API 요청/응답 DTO. 멤버 색은 화면 표시용이라 웹 계층에서 정한다(가입 순 팔레트). */
public final class MapDtos {

    private MapDtos() {}

    /** 가입 순서대로 배정하는 멤버 색(최대 4명). */
    static final List<String> MEMBER_COLORS = List.of("#2fc3ad", "#e8743b", "#3b6fd6", "#9b59d6");

    /** 지도를 만들 때 보여 주는 공유 지도 3줄 규칙. */
    static List<String> rules(int leaveGraceDays) {
        return List.of(
            "같은 지역을 멤버가 각자 칠할 수 있어요. 지역 색은 먼저 칠한 사람(선점자)의 색이에요.",
            "지도장은 사진 필수·하루 상한을 정하고, 의심스러운 방문에 이의를 표시할 수 있어요.",
            "탈퇴하면 내 영토가 지도에서 사라지고 선점은 다음 사람에게 넘어가요(" + leaveGraceDays + "일 안에 돌아오면 복구).");
    }

    public record CreateMapRequest(@NotBlank String name, String countryCode) {}

    public record JoinMapRequest(@NotBlank String inviteCode) {}

    public record TransferOwnerRequest(@NotBlank String explorerId) {}

    public record SettingsRequest(@NotNull Boolean photoRequired, @NotNull Integer dailyCheckInCap, String visibility) {}

    public record DisputeRequest(@NotNull Boolean disputed) {}

    public record SettingsResponse(boolean photoRequired, int dailyCheckInCap, String visibility) {
        static SettingsResponse of(ExpeditionMap map) {
            return new SettingsResponse(map.settings().photoRequired(), map.settings().dailyCheckInCap(),
                map.settings().visibility().name());
        }
    }

    /** GET /maps 한 줄. */
    public record MapSummaryResponse(String mapId, String name, String kind, String role, int memberCount, String inviteCode,
                                     Instant createdAt) {
        static MapSummaryResponse of(ExpeditionMap map, ExplorerId viewer) {
            return new MapSummaryResponse(map.id().value(), map.name(), map.kind().name(),
                map.member(viewer).map(member -> member.role().name()).orElse(null), map.members().size(),
                map.inviteCode().value(), map.createdAt());
        }
    }

    /**
     * @param regionCount 이 멤버의 이 지도 영토 수(탈퇴하면 이만큼 지도에서 사라진다)
     * @param claimCount  이 멤버가 선점자인 지역 수
     */
    public record MemberResponse(String explorerId, String role, Instant joinedAt, String color, boolean me,
                                 int regionCount, int claimCount) {}

    public record DisputeResponse(String regionCode, String explorerId) {}

    /**
     * GET /maps/{id}.
     *
     * @param claims    지역마다 선점자(지역 색 = 그 멤버 색)
     * @param disputed  지도장이 이의 표시한 방문
     * @param departing 탈퇴 유예 중인 사람 수(유예 안에 돌아오면 영토 복구)
     * @param rules     공유 지도 3줄 규칙
     */
    public record MapDetailResponse(String mapId, String name, String kind, String countryCode, String ownerId,
                                    String inviteCode, SettingsResponse settings, List<MemberResponse> members,
                                    List<ExplorationDtos.ClaimResponse> claims, List<DisputeResponse> disputed,
                                    int departing, List<String> rules, boolean rejoined) {

        static MapDetailResponse of(MapView view, ExplorerId viewer, int leaveGraceDays, boolean rejoined) {
            ExpeditionMap map = view.map();
            Territory territory = view.territory();
            List<Visit> claims = territory.claims();
            List<Member> ordered = map.membersByJoinOrder();
            List<MemberResponse> members = ordered.stream().map(member -> new MemberResponse(member.explorerId().value(),
                member.role().name(), member.joinedAt(), MEMBER_COLORS.get(ordered.indexOf(member) % MEMBER_COLORS.size()),
                member.explorerId().equals(viewer), territory.visitsOf(member.explorerId()).size(),
                territory.claimCountOf(member.explorerId()))).toList();
            return new MapDetailResponse(map.id().value(), map.name(), map.kind().name(), map.country().value(),
                map.ownerId().value(), map.inviteCode().value(), SettingsResponse.of(map), members,
                claims.stream().map(claim -> new ExplorationDtos.ClaimResponse(claim.regionCode().value(),
                    claim.checkedInBy().value())).toList(),
                territory.disputedVisits().stream()
                    .map(visit -> new DisputeResponse(visit.regionCode().value(), visit.checkedInBy().value())).toList(),
                map.departures().size(), MapDtos.rules(leaveGraceDays), rejoined);
        }
    }

    /** POST /maps/{id}/leave. @param hiddenRegionCount 지도에서 사라진 내 영토 수, @param restoreUntil 이때까지 다시 합류하면 복구 */
    public record LeaveResponse(String mapId, int hiddenRegionCount, Instant restoreUntil, String message) {}

    public record InviteCodeResponse(String mapId, String inviteCode) {}
}
