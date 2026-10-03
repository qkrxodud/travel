package com.kobi.territory.social.domain.feed;

import com.kobi.territory.common.model.Rarity;

/**
 * 소식 내용(feed_entry.payload JSON). 종류마다 쓰는 값만 채운다 — 체크인 = 지역·희귀도, 테마 완성 = 테마 id, 레벨 업 = 레벨,
 * 뱃지 = 뱃지 id. 이름·문구는 화면이 카탈로그로 그린다(소셜은 카탈로그를 참조하지 않는다 — 의존 매트릭스). 메모·사진·방문일 없음(§7).
 */
public record FeedDetail(String regionCode, Rarity rarity, String themeId, Integer level, String badgeId) {

    public static FeedDetail visit(String regionCode, Rarity rarity) {
        return new FeedDetail(regionCode, rarity, null, null, null);
    }

    public static FeedDetail theme(String themeId) {
        return new FeedDetail(null, null, themeId, null, null);
    }

    public static FeedDetail level(int level) {
        return new FeedDetail(null, null, null, level, null);
    }

    public static FeedDetail badge(String badgeId) {
        return new FeedDetail(null, null, null, null, badgeId);
    }

    /** 같은 소식인지 가르는 대상(같은 사람·같은 종류·같은 대상은 한 번만 보인다 — ActivityFeed). */
    String subject() {
        if (regionCode != null) return regionCode;
        if (themeId != null) return themeId;
        if (badgeId != null) return badgeId;
        return String.valueOf(level);
    }
}
