package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.progression.domain.quest.QuestPeriod;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.time.YearMonth;

/**
 * XP 장부·보호권 장부 refId 규칙(멱등 키). implement-context 이벤트 규칙 4 + 2단계 리더 결정 D3·D4 + 8단계.
 * <ul>
 *   <li>기본 XP 지급 {@code region:{e}:{code}#{k}}, 회수 {@code region:{e}:{code}#{k}:revoke} — k 는 세대(재체크인마다 +1)</li>
 *   <li>시·도 첫 발 {@code province:{e}:{provinceCode}}</li>
 *   <li>선점 {@code claim:{mapId}:{code}:{e}}</li>
 *   <li>테마(세트) 완성 {@code set:{e}:{themeId}} — 접두사 "set" 은 저장된 계약이라 유지</li>
 *   <li>퀘스트 {@code quest:{e}:{period}:{questId}}</li>
 *   <li>8단계: 미스터리 {@code mystery:{e}:{weekStart}}, 시·도 정복 {@code conquest:{e}:{provinceCode}},
 *       연속 탐험 마일스톤 {@code milestone:{e}:{months}}</li>
 *   <li>9단계: 계절 회차 완성 {@code season:{e}:{roundId}}, 재방문 도장 {@code revisit:{e}:{code}@{year}},
 *       가고 싶은 곳 {@code wish:{e}:{code}}</li>
 *   <li>8단계 보호권 장부: 월간 퀘스트 완주 {@code freeze:{e}:quests:{yyyy-MM}}, 마일스톤 {@code freeze:{e}:milestone:{months}},
 *       사용 {@code freeze:{e}:use:{yyyy-MM}}(연속을 이은 달)</li>
 * </ul>
 */
public final class RefIds {

    static final String REVOKE = ":revoke";

    private RefIds() {}

    static String regionPrefix(ExplorerId explorer, RegionCode region) {
        return "region:" + explorer.value() + ":" + region.value() + "#";
    }

    public static String regionGrant(ExplorerId explorer, RegionCode region, int generation) {
        return regionPrefix(explorer, region) + generation;
    }

    public static String regionRevoke(ExplorerId explorer, RegionCode region, int generation) {
        return regionGrant(explorer, region, generation) + REVOKE;
    }

    public static String province(ExplorerId explorer, String provinceCode) {
        return "province:" + explorer.value() + ":" + provinceCode;
    }

    public static String claim(String mapId, RegionCode region, ExplorerId explorer) {
        return "claim:" + mapId + ":" + region.value() + ":" + explorer.value();
    }

    public static String theme(ExplorerId explorer, String themeId) {
        return "set:" + explorer.value() + ":" + themeId;
    }

    public static String quest(ExplorerId explorer, QuestPeriod period, String questId) {
        return "quest:" + explorer.value() + ":" + period.value() + ":" + questId;
    }

    public static String mystery(ExplorerId explorer, String weekId) {
        return "mystery:" + explorer.value() + ":" + weekId;
    }

    public static String conquest(ExplorerId explorer, String provinceCode) {
        return "conquest:" + explorer.value() + ":" + provinceCode;
    }

    public static String milestone(ExplorerId explorer, int months) {
        return "milestone:" + explorer.value() + ":" + months;
    }

    /** 계절 한정 테마 회차 완성 season:{e}:{roundId}(9단계). */
    public static String season(ExplorerId explorer, String roundId) {
        return "season:" + explorer.value() + ":" + roundId;
    }

    /** 재방문 도장 revisit:{e}:{code}@{year}(9단계 — 마지막 마디가 지역@연도). */
    public static String revisit(ExplorerId explorer, RegionCode region, int year) {
        return "revisit:" + explorer.value() + ":" + region.value() + "@" + year;
    }

    /** 가고 싶은 곳 다녀옴 wish:{e}:{code}(9단계 — 지역당 한 번). */
    public static String wish(ExplorerId explorer, RegionCode region) {
        return "wish:" + explorer.value() + ":" + region.value();
    }

    public static String freezeFromQuests(ExplorerId explorer, QuestPeriod period) {
        return "freeze:" + explorer.value() + ":quests:" + period.value();
    }

    public static String freezeFromMilestone(ExplorerId explorer, int months) {
        return "freeze:" + explorer.value() + ":milestone:" + months;
    }

    public static String freezeUse(ExplorerId explorer, YearMonth month) {
        return "freeze:" + explorer.value() + ":use:" + month;
    }

    /** refId 의 마지막 마디(시·도 코드·개월 수·주 id 등 — 출처별 형식은 위 목록). */
    static String subjectOf(String refId) {
        return refId.substring(refId.lastIndexOf(':') + 1);
    }

    /** 퀘스트 refId 의 기간(quest:{e}:{period}:{questId}). */
    static QuestPeriod questPeriodOf(String refId) {
        String withoutQuest = refId.substring(0, refId.lastIndexOf(':'));
        return new QuestPeriod(withoutQuest.substring(withoutQuest.lastIndexOf(':') + 1));
    }
}
