package com.kobi.territory.catalog.api.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 관리자 계절 회차 지역 목록 응답(13s단계, /admin/seasons/**). 계약: _workspace/13s_contracts.md */
public final class AdminSeasonDtos {

    private AdminSeasonDtos() {}

    /**
     * @param tourApi  TourAPI 연결 현황
     * @param schedule 자동 수집 정책
     * @param rounds   지금 열린 회차(있으면) → 계절마다 다음 회차
     */
    public record SeasonLineupsResponse(TourApiStatusResponse tourApi, ScheduleResponse schedule, List<RoundLineupResponse> rounds) {}

    /**
     * @param callsToday 오늘(서울 날짜) 이 서비스의 호출 수(재기동해도 이어진다)
     * @param dailyLimit 이 서비스의 하루 호출 상한
     * @param exhausted  오늘 상한에 닿아 더는 호출하지 않는지
     * @param warnings   키 없음·상한 도달 경고
     */
    public record TourApiStatusResponse(boolean configured, int callsToday, int dailyLimit, boolean exhausted, String source,
                                        List<String> warnings) {}

    public record ScheduleResponse(int leadDays, long recollectAfterHours, boolean autoConfirm, int autoConfirmMinRegions) {}

    /**
     * @param locked            회차가 열려(또는 지나) 목록이 고정됨 — 갱신·확정 불가
     * @param collectionOpensAt 자동 수집·확정을 시작하는 시각(시작 leadDays 일 전)
     * @param nextPlan          지금 자동 수집이 돈다면 할 일 NONE | PREVIEW | COLLECT | COLLECT_AND_CONFIRM
     * @param inEffect          이 회차에 쓰는(쓸) 목록
     * @param candidate         확정 전 후보(없으면 null)
     * @param lastAttempt       마지막 수집 시도(없으면 null)
     * @param warnings          먼저 보일 경고(키 없음·실패 까닭·부족분 채움)
     */
    public record RoundLineupResponse(String roundId, String seasonId, String name, String emoji, int year, Instant startsAt,
                                      Instant endsAt, boolean locked, Instant collectionOpensAt, String nextPlan,
                                      LineupResponse inEffect, CandidateResponse candidate, AttemptResponse lastAttempt,
                                      List<String> warnings) {}

    /**
     * @param provenance  "tourapi" | "ai-estimate" | "mixed"
     * @param source      근거 기관(근거 지역이 있으면 "한국관광공사 TourAPI")
     * @param confirmedBy AUTO | ADMIN | null(기본 목록)
     * @param collectedAt 근거 자료를 읽은 시각(기본 목록이면 null)
     */
    public record LineupResponse(String provenance, String source, String confirmedBy, Instant confirmedAt, Instant collectedAt,
                                 List<LineupRegionResponse> regions) {}

    /** @param evidencedRegions TourAPI 근거 지역 수 */
    public record CandidateResponse(String provenance, String source, Instant collectedAt, int evidencedRegions,
                                    List<LineupRegionResponse> regions, List<String> warnings) {}

    /** @param outcome COLLECTED | PARTIAL | NOT_CONFIGURED | KEY_REJECTED | QUOTA_EXCEEDED | BAD_RESPONSE | UNREACHABLE */
    public record AttemptResponse(Instant at, String outcome, boolean failed, List<String> warnings) {}

    /** @param provenance "tourapi" | "ai-estimate" */
    public record LineupRegionResponse(String code, String name, String provinceCode, String provenance,
                                       List<EvidenceResponse> evidence) {}

    /**
     * @param evidenceKind FESTIVAL(축제 — 기간 있음) | ATTRACTION(관광지 — startDate·endDate null)
     * @param contentId    TourAPI 콘텐츠 id
     * @param fetchedAt    TourAPI 조회 시각
     */
    public record EvidenceResponse(String contentId, String title, LocalDate startDate, LocalDate endDate, Instant fetchedAt,
                                   String evidenceKind) {}
}
