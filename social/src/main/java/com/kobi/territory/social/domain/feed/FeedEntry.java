package com.kobi.territory.social.domain.feed;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.Rarity;
import java.time.Instant;
import java.util.Objects;

/**
 * 친구 소식 한 건(feed_entry — 읽기 모델 행, 애그리거트 아님). 공개 이벤트 한 건에서 만들어지고 이벤트로 언제든 다시 만들 수 있다
 * (재구성: 다음 세대에 outbox 를 처음부터 재생한 뒤 바꾼다). refId 는 원본 이벤트의 멱등 키 — 같은 이벤트가 두 번 와도(최소 1회 전달·재생) 한 행이다.
 * 이벤트에서 만든 값 그대로 넘기는 읽기 전용 데이터라 record(class vs record 기준).
 *
 * @param mapId           체크인한 지도(체크인만, 탈퇴 숨김 반영용) — 다른 종류는 null
 * @param visitGeneration 체크인 회차(체크인만, 1부터) — 취소가 자기 회차 이하만 거두게(QA r2 P2-B). 0 = 모름(다른 종류·예전 이벤트·병합으로 회차가 바뀐 방문)
 */
public record FeedEntry(String refId, ExplorerId actorId, String mapId, FeedKind kind, FeedDetail detail, Instant occurredAt,
                        int visitGeneration) {

    public FeedEntry {
        Objects.requireNonNull(refId, "refId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (visitGeneration < 0) throw new IllegalArgumentException("visitGeneration=" + visitGeneration);
    }

    /** 체크인이 아닌 소식(회차 없음). */
    public FeedEntry(String refId, ExplorerId actorId, String mapId, FeedKind kind, FeedDetail detail, Instant occurredAt) {
        this(refId, actorId, mapId, kind, detail, occurredAt, 0);
    }

    /** 체크인(RegionVisited) — 지도·지역·멤버·회차가 같으면 같은 소식. 회차를 모르는 예전 이벤트(0)는 처리 시각으로 가른다. */
    public static FeedEntry visit(ExplorerId actor, String mapId, String regionCode, Rarity rarity, int generation, Instant at) {
        return new FeedEntry(visitRef(actor, mapId, regionCode, generation, at), actor, Objects.requireNonNull(mapId, "mapId"),
            FeedKind.VISIT, FeedDetail.visit(regionCode, rarity), at, Math.max(0, generation));
    }

    /** 테마(세트) 완성(SetCompleted, 수령자마다) — 탐험가당 테마당 한 번(진행 XP refId set:{e}:{setId} 와 같은 단위). */
    public static FeedEntry themeCompleted(ExplorerId actor, String themeId, Instant at) {
        return new FeedEntry("theme:" + actor.value() + ":" + themeId, actor, null, FeedKind.THEME_COMPLETED,
            FeedDetail.theme(themeId), at);
    }

    /** 레벨 업(LevelUp) — 탐험가당 레벨당 한 번(취소로 내려갔다 다시 올라도 한 번). */
    public static FeedEntry levelUp(ExplorerId actor, int level, Instant at) {
        return new FeedEntry("level:" + actor.value() + ":" + level, actor, null, FeedKind.LEVEL_UP, FeedDetail.level(level), at);
    }

    /** 뱃지 획득(BadgeEarned) — 탐험가당 뱃지당 한 번. */
    public static FeedEntry badgeEarned(ExplorerId actor, String badgeId, Instant at) {
        return new FeedEntry("badge:" + actor.value() + ":" + badgeId, actor, null, FeedKind.BADGE_EARNED,
            FeedDetail.badge(badgeId), at);
    }

    /** 연속 탐험 마일스톤(StreakMilestoneReached, 8단계) — 탐험가당 마일스톤당 한 번(진행 refId milestone:{e}:{n} 과 같은 단위). */
    public static FeedEntry milestoneReached(ExplorerId actor, int months, Instant at) {
        return new FeedEntry("milestone:" + actor.value() + ":" + months, actor, null, FeedKind.STREAK_MILESTONE,
            FeedDetail.milestone(months), at);
    }

    /** 시·도 정복(ProvinceConquered, 8단계) — 탐험가당 시·도당 한 번. */
    public static FeedEntry provinceConquered(ExplorerId actor, String provinceCode, Instant at) {
        return new FeedEntry("conquest:" + actor.value() + ":" + provinceCode, actor, null, FeedKind.PROVINCE_CONQUERED,
            FeedDetail.conquest(provinceCode), at);
    }

    /** 이번 주 미스터리 지역 발견(MysteryBonusEarned, 8단계) — 탐험가당 주당 한 번. */
    public static FeedEntry mysteryFound(ExplorerId actor, String regionCode, String weekStart, Instant at) {
        return new FeedEntry("mystery:" + actor.value() + ":" + weekStart, actor, null, FeedKind.MYSTERY_FOUND,
            FeedDetail.mystery(regionCode, weekStart), at);
    }

    /** 체크인 소식의 멱등 키(같은 체크인 이벤트가 두 번 와도 한 행). 취소는 refId 가 아니라 (주인, 지도, 지역)으로 거둔다(병합 대응). */
    public static String visitRef(ExplorerId actor, String mapId, String regionCode, int generation, Instant at) {
        String round = generation > 0 ? "#" + generation : "@" + at.toEpochMilli();
        return "visit:" + mapId + ":" + regionCode + ":" + actor.value() + round;
    }

    /** 같은 사람의 같은 소식(같은 종류·같은 대상)인지 묶는 키. */
    String sameNewsKey() {
        return actorId.value() + "|" + kind + "|" + detail.subject();
    }

    /** 병합(익명 → 계정)으로 소식의 주인을 계정 탐험가로 옮긴 사본(refId 는 원본 이벤트 그대로). */
    public FeedEntry attributedTo(ExplorerId into) {
        return new FeedEntry(refId, into, mapId, kind, detail, occurredAt, visitGeneration);
    }
}
