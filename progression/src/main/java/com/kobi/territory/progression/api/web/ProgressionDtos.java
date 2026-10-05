package com.kobi.territory.progression.api.web;

import com.kobi.territory.common.model.Rarity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 진행 API 응답·요청 DTO. */
public final class ProgressionDtos {

    private ProgressionDtos() {}

    public record TitleRef(String id, String name) {}

    /**
     * @param months        이번 달 기준 연속 개월 수(지난달까지 이어졌으면 유지, 그보다 오래면 0 — 8단계: 이번 달에 칠하면 가진 보호권으로
     *                      빈 달을 메울 수 있으면 유지)
     * @param freezesNeeded 이번 달에 칠하면 쓰게 될 보호권 수(빈 달 수, 8단계). 0 = 쓸 일 없음. 가진 것보다 많으면 칠할 때 끊긴다(1부터)
     * @param frozenMonths  지금 연속 구간 안에서 보호권으로 메운 달 전부(yyyy-MM, 오래된 순, 8단계 보완). 연속이 끊겼으면 빈 목록
     */
    public record StreakResponse(int months, String lastMonth, boolean activeThisMonth, int freezesNeeded,
                                 List<String> frozenMonths) {}

    /**
     * 보호권(8단계).
     *
     * @param held     가진 보호권 수
     * @param max      최대 보유 수(설정 — 카탈로그 streak-rules.json)
     * @param lastUsed 가장 최근에 쓴 기록(없으면 null) — 화면 토스트 "보호권 n개로 스트릭을 지켰어요"
     */
    public record StreakFreezeResponse(int held, int max, FreezeUseResponse lastUsed, MonthlyFreezeResponse thisMonth) {}

    /**
     * 이번 달 월간 퀘스트로 받는 보호권 진행(8단계 QA P3-2 — 서버 판정 값).
     *
     * @param questsRewarded 보상을 받은 월간 퀘스트 수
     * @param questsRequired 모두 받아야 하는 수
     * @param reward         모두 받으면 주는 보호권 수
     * @param earned         이번 달 몫을 채웠는지(모두 받음 — 상한에 막혀 0개였어도 true)
     * @param granted        이번 달 몫으로 실제로 늘어난 보호권 수(상한이면 0)
     */
    public record MonthlyFreezeResponse(String month, int questsRewarded, int questsRequired, int reward, boolean earned,
                                        int granted) {}

    /** @param month 연속을 이은 달(yyyy-MM), @param count 그때 쓴 보호권 수, @param at 처리 시각 */
    public record FreezeUseResponse(String month, int count, Instant at) {}

    /**
     * 연속 탐험 마일스톤 한 단계(8단계).
     *
     * @param reachedAt       받은 시각(아직이면 null)
     * @param remainingMonths 지금 연속에서 더 필요한 개월 수(받았으면 0)
     */
    public record MilestoneResponse(int months, int xp, int freezes, String titleId, String titleName, boolean reached,
                                    Instant reachedAt, int remainingMonths) {}

    /**
     * 탐험가 단위 시·도 칠하기 현황(8단계 — 현행 지역 기준, 모든 지도 합산).
     *
     * @param complete    지금 현행 지역을 모두 칠한 상태
     * @param conquered   정복 기록이 있음(왕관 — 취소해도 사라지지 않는다)
     * @param conqueredAt 정복 시각(없으면 null)
     */
    public record ProvinceProgressResponse(String code, String name, int visited, int total, int percent, boolean complete,
                                           boolean conquered, Instant conqueredAt) {}

    public record BadgeResponse(String id, String ico, String name, String desc, boolean earned, Instant earnedAt) {}

    public record TitleResponse(String id, String name, String how, String source, boolean earned, boolean selected) {}

    public record XpEntryResponse(String source, int amount, String refId, Instant at) {}

    /**
     * GET /progress. 이벤트로 비동기 반영되는 값이다(체크인 직후 몇 백 ms 늦을 수 있음).
     *
     * @param title             화면에 보일 칭호(선택 칭호, 없으면 레벨 칭호)
     * @param streakFreeze      보호권(8단계)
     * @param nextMilestone     아직 받지 않은 가장 가까운 연속 탐험 마일스톤(모두 받았으면 null, 8단계)
     * @param milestones        연속 탐험 마일스톤 전체(8단계)
     * @param provinces         시·도별 칠하기·정복 현황(8단계, 표시 순서)
     * @param mysteryFoundCount 이번 주 미스터리 보너스를 받은 주 수(8단계)
     */
    public record ProgressResponse(String explorerId, long xp, int level, String levelTitle, long currentLevelXp,
                                   long nextLevelXp, TitleRef title, String selectedTitleId, StreakResponse streak,
                                   int badgeCount, List<BadgeResponse> badges, List<TitleResponse> titles,
                                   List<XpEntryResponse> recentXp, StreakFreezeResponse streakFreeze,
                                   MilestoneResponse nextMilestone, List<MilestoneResponse> milestones,
                                   List<ProvinceProgressResponse> provinces, int mysteryFoundCount, int revisitStampCount,
                                   int wishFulfilledCount) {}

    /**
     * 계절 회차의 지역 하나 — collected = 이 회차 기간 안에 이 지도에서 칠해 센 지역.
     *
     * @param provenance 이 지역을 고른 근거의 출처(13s단계): "tourapi"(한국관광공사 TourAPI 축제) | "ai-estimate"(AI 추정, 검증 전)
     * @param evidence   근거(tourapi 지역만 — 축제 이른 순 → 관광지, 최대 5개), ai-estimate 는 빈 목록
     */
    public record SeasonRegionResponse(String code, String name, String provinceCode, boolean collected, String provenance,
                                       List<SeasonEvidenceResponse> evidence) {}

    /**
     * 근거 하나(13s단계).
     *
     * @param contentId    한국관광공사 TourAPI 콘텐츠 id
     * @param startDate    축제 첫날(관광지는 null)
     * @param endDate      축제 마지막 날(관광지는 null)
     * @param fetchedAt    TourAPI 조회 시각
     * @param evidenceKind "FESTIVAL"(축제) | "ATTRACTION"(관광지) — 화면은 "축제"/"관광지"로 표시
     */
    public record SeasonEvidenceResponse(String contentId, String title, LocalDate startDate, LocalDate endDate,
                                         Instant fetchedAt, String evidenceKind) {}

    /**
     * 계절 한정 테마 회차 하나(9단계).
     *
     * @param roundId          {계절}-{연도}(예 autumn-2026)
     * @param name             표시 이름(예 "2026 단풍 명소")
     * @param endsAt           닫히는 순간(마지막 날 다음 날 00:00, 서비스 시간대) — 이 순간부터 닫힘
     * @param remainingSeconds 지금 열린 회차면 닫힐 때까지 남은 초, 닫힌 회차는 0
     * @param open             지금 열려 있는지
     * @param rewarded         내가 완성 보상(XP·칭호·배경)을 받은 수령자인지(완성 시점 멤버)
     * @param backgroundItemId 회차 배경 아이템 id(season:{roundId})
     * @param provenance       회차 지역 목록 전체의 출처 요약(13s단계): "tourapi" | "ai-estimate" | "mixed"
     * @param source           근거 기관 표기 — 근거 지역이 있으면 "한국관광공사 TourAPI", 없으면 null
     */
    public record SeasonRoundResponse(String roundId, String seasonId, String name, String emoji, int year, Instant startsAt,
                                      Instant endsAt, long remainingSeconds, boolean open, int have, int total,
                                      boolean completed, Instant completedAt, boolean rewarded, int xp, String titleId,
                                      String titleName, String backgroundItemId, List<SeasonRegionResponse> regions,
                                      String provenance, String source) {}

    /** 다음에 열리는 회차(지금 열린 회차가 없을 때 안내용). @param provenance 그 회차 지역 목록의 출처 요약(13s단계) */
    public record NextSeasonResponse(String roundId, String seasonId, String name, String emoji, Instant startsAt, Instant endsAt,
                                     String provenance) {}

    /**
     * GET /seasons/current(9단계) — mapId 생략 시 개인 지도.
     *
     * @param current 지금 열린 회차(없으면 빈 목록 — 봄·가을 기간이 겹치지 않아 보통 0~1개)
     * @param next    다음에 열리는 회차(없으면 null)
     * @param history 이 지도에 기록이 있는 닫힌 회차(최근 순, 미완성 기록 포함)
     */
    public record SeasonsResponse(String mapId, Instant now, List<SeasonRoundResponse> current, NextSeasonResponse next,
                                  List<SeasonRoundResponse> history) {}

    /** 이번 주 미스터리 지역(8단계). */
    public record MysteryRegionResponse(String code, String name, String provinceCode, String provinceName, Rarity rarity) {}

    /**
     * GET /mystery/this-week.
     *
     * @param weekStart        그 주 월요일(yyyy-MM-dd) — 주 id
     * @param endsAt           주가 끝나는 순간(다음 월요일 00:00, 서비스 시간대)
     * @param remainingSeconds 끝날 때까지 남은 초
     * @param bonusXp          그 주에 칠하면 받는 보너스 XP(주마다 한 번)
     * @param received         내가 이번 주 보너스를 받았는지(체크인 뒤 비동기로 반영)
     * @param receivedAt       받은 시각(아직이면 null)
     * @param foundCount       지금까지 보너스를 받은 주 수(뱃지 "미스터리 탐험가" 1·5·10회)
     * @param revealed         지역 이름을 화면에 바로 공개해도 되는지(QA P3-8) — 이번 주 보너스를 받았으면 true(직접 칠한 곳이라 숨길
     *                         이유가 없다). false 면 화면은 지금처럼 ❓ 마커·카드를 누른 뒤에 이름을 보여 준다
     */
    public record MysteryWeekResponse(String weekStart, Instant startsAt, Instant endsAt, long remainingSeconds,
                                      MysteryRegionResponse region, int bonusXp, boolean received, Instant receivedAt,
                                      int foundCount, boolean revealed) {}

    /** PUT /progress/title. titleId 가 null·빈 문자열이면 선택 해제(레벨 칭호 표시). */
    public record SelectTitleRequest(String titleId) {}

    public record SetRegionResponse(String code, String name, boolean collected) {}

    public record SetResponse(String id, String name, String desc, String title, int have, int total, boolean completed,
                              Instant completedAt, int rewardXp, List<SetRegionResponse> regions) {}

    /** GET /collection — 지도 기준 도감. */
    public record CollectionResponse(String mapId, int completed, int total, List<SetResponse> sets) {}

    public record QuestResponse(String id, String scope, String ico, String name, String desc, int current, int target,
                                int xp, String title, boolean achieved, boolean claimed, Instant claimedAt,
                                boolean claimable) {}

    /** GET /quests — 이번 달(month) 월간 퀘스트 + 상시 도전. */
    public record QuestsResponse(String month, int monthlyDone, List<QuestResponse> monthly, List<QuestResponse> always) {}

    /** POST /quests/{questId}/claim — XP 는 진행에 비동기로 반영된다. */
    public record ClaimResponse(String questId, String period, int xp, Instant claimedAt) {}
}
