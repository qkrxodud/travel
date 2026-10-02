package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.progression.domain.quest.QuestPeriod;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;

/**
 * XP 장부 refId 규칙(멱등 키). implement-context 이벤트 규칙 4 + 2단계 리더 결정 D3·D4.
 * <ul>
 *   <li>기본 XP 지급 {@code region:{e}:{code}#{k}}, 회수 {@code region:{e}:{code}#{k}:revoke} — k 는 세대(재체크인마다 +1)</li>
 *   <li>시·도 첫 발 {@code province:{e}:{provinceCode}}</li>
 *   <li>선점 {@code claim:{mapId}:{code}:{e}}</li>
 *   <li>테마(세트) 완성 {@code set:{e}:{themeId}} — 접두사 "set" 은 저장된 계약이라 유지</li>
 *   <li>퀘스트 {@code quest:{e}:{period}:{questId}}</li>
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
}
