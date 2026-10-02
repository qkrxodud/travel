package com.kobi.territory.progression.api.web;

import java.time.Instant;
import java.util.List;

/** 진행 API 응답·요청 DTO. */
public final class ProgressionDtos {

    private ProgressionDtos() {}

    public record TitleRef(String id, String name) {}

    /** @param months 이번 달 기준 연속 개월 수(지난달까지 이어졌으면 유지, 그보다 오래면 0) */
    public record StreakResponse(int months, String lastMonth, boolean activeThisMonth) {}

    public record BadgeResponse(String id, String ico, String name, String desc, boolean earned, Instant earnedAt) {}

    public record TitleResponse(String id, String name, String how, String source, boolean earned, boolean selected) {}

    public record XpEntryResponse(String source, int amount, String refId, Instant at) {}

    /**
     * GET /progress. 이벤트로 비동기 반영되는 값이다(체크인 직후 몇 백 ms 늦을 수 있음).
     *
     * @param title 화면에 보일 칭호(선택 칭호, 없으면 레벨 칭호)
     */
    public record ProgressResponse(String explorerId, long xp, int level, String levelTitle, long currentLevelXp,
                                   long nextLevelXp, TitleRef title, String selectedTitleId, StreakResponse streak,
                                   int badgeCount, List<BadgeResponse> badges, List<TitleResponse> titles,
                                   List<XpEntryResponse> recentXp) {}

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
