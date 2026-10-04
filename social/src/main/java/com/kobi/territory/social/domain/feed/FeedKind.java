package com.kobi.territory.social.domain.feed;

/**
 * 친구 소식 종류 — 체크인(발 도장)·테마(세트) 완성·레벨 업·뱃지 획득, 8단계: 연속 탐험 마일스톤·시·도 정복·이번 주 미스터리 지역 발견,
 * 9단계: 계절 한정 테마 회차 완성·재방문 도장(가고 싶은 곳은 비공개라 소식 없음).
 * 메모·사진은 어느 종류에도 없다. 저장 값(feed_entry.kind)이라 이름을 바꾸지 않는다.
 */
public enum FeedKind {
    VISIT, THEME_COMPLETED, LEVEL_UP, BADGE_EARNED, STREAK_MILESTONE, PROVINCE_CONQUERED, MYSTERY_FOUND, SEASON_COMPLETED,
    REVISIT_STAMPED
}
