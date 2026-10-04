package com.kobi.territory.social.domain.feed;

import com.kobi.territory.common.model.Rarity;

/**
 * 소식 내용(feed_entry.payload JSON). 종류마다 쓰는 값만 채운다 — 체크인 = 지역·희귀도, 테마 완성 = 테마 id, 레벨 업 = 레벨,
 * 뱃지 = 뱃지 id, 8단계: 마일스톤 = 개월 수, 시·도 정복 = 시·도 코드, 미스터리 = 지역·주(월요일), 9단계: 계절 = 회차 id,
 * 재방문 도장 = 지역·연도. 이름·문구는 화면이 카탈로그로 그린다
 * (소셜은 카탈로그를 참조하지 않는다 — 의존 매트릭스). 메모·사진·방문일 없음(§7). 예전 행에 없는 값은 비어 있다.
 */
public record FeedDetail(String regionCode, Rarity rarity, String themeId, Integer level, String badgeId, String provinceCode,
                         Integer months, String weekStart, String roundId, Integer year) {

    /** 9단계 이전 형식(회차·연도 없음). */
    public FeedDetail(String regionCode, Rarity rarity, String themeId, Integer level, String badgeId, String provinceCode,
                      Integer months, String weekStart) {
        this(regionCode, rarity, themeId, level, badgeId, provinceCode, months, weekStart, null, null);
    }

    public static FeedDetail visit(String regionCode, Rarity rarity) {
        return new FeedDetail(regionCode, rarity, null, null, null, null, null, null);
    }

    public static FeedDetail theme(String themeId) {
        return new FeedDetail(null, null, themeId, null, null, null, null, null);
    }

    public static FeedDetail level(int level) {
        return new FeedDetail(null, null, null, level, null, null, null, null);
    }

    public static FeedDetail badge(String badgeId) {
        return new FeedDetail(null, null, null, null, badgeId, null, null, null);
    }

    public static FeedDetail milestone(int months) {
        return new FeedDetail(null, null, null, null, null, null, months, null);
    }

    public static FeedDetail conquest(String provinceCode) {
        return new FeedDetail(null, null, null, null, null, provinceCode, null, null);
    }

    public static FeedDetail mystery(String regionCode, String weekStart) {
        return new FeedDetail(regionCode, null, null, null, null, null, null, weekStart);
    }

    /** 계절 한정 테마 회차 완성(9단계) — 회차 id. */
    public static FeedDetail season(String roundId) {
        return new FeedDetail(null, null, null, null, null, null, null, null, roundId, null);
    }

    /** 재방문 도장(9단계) — 지역·연도(같은 지역이어도 해마다 다른 소식). */
    public static FeedDetail revisit(String regionCode, int year) {
        return new FeedDetail(regionCode, null, null, null, null, null, null, null, null, year);
    }

    /** 같은 소식인지 가르는 대상(같은 사람·같은 종류·같은 대상은 한 번만 보인다 — ActivityFeed). 미스터리는 주마다 다른 소식. */
    String subject() {
        if (weekStart != null) return weekStart;
        if (roundId != null) return roundId;
        if (year != null) return regionCode + "@" + year;
        if (regionCode != null) return regionCode;
        if (themeId != null) return themeId;
        if (badgeId != null) return badgeId;
        if (provinceCode != null) return provinceCode;
        if (months != null) return String.valueOf(months);
        return String.valueOf(level);
    }
}
