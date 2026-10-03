import { describe, expect, it } from 'vitest';
import type { FeedItemResponse } from '../../../api/types/social';
import { CATALOG } from '../../../test/fixtures';
import { avatarColor, compareClasses, displayHandle, feedParts, initialOf, leadText, regionNames, relationOf, shareOfAll } from './social';
import { normalizeHandle } from '../../../shared/lib/handle';

const feed = (overrides: Partial<FeedItemResponse>): FeedItemResponse => ({
  handle: 'lee', kind: 'VISIT', regionCode: null, rarity: null, themeId: null, level: null, badgeId: null, daysAgo: 0, when: '오늘', ...overrides,
});

describe('소셜 표시', () => {
  it('아바타 색은 handle 로 고정', () => {
    expect(avatarColor('kim')).toBe(avatarColor('kim'));
    expect(avatarColor(null)).toMatch(/^#[0-9a-f]{6}$/);
  });

  it('handle 표시·입력 정리', () => {
    expect(displayHandle('kim')).toBe('@kim');
    expect(displayHandle(null)).toBe('탐험가');
    expect(initialOf('kim')).toBe('K');
    expect(initialOf(null, 'M')).toBe('M');
    expect(normalizeHandle('  @lee_travels ')).toBe('lee_travels');
    expect(relationOf({ handle: 'a', following: true, follower: true, mutual: true })).toBe('mutual');
    expect(relationOf({ handle: 'a', following: false, follower: true, mutual: false })).toBe('follower');
    expect(shareOfAll(45, 250)).toBe(18);
  });

  it('친구 소식 문장', () => {
    expect(feedParts(feed({ regionCode: 'KR-37430', rarity: 'LEGEND' }), CATALOG, () => undefined, () => undefined)).toEqual({ strong: '경북 울릉군', text: '에 발 도장 · 전설 지역' });
    expect(feedParts(feed({ kind: 'THEME_COMPLETED', themeId: 'jiri' }), CATALOG, () => '지리산 둘레', () => undefined)).toEqual({ strong: '지리산 둘레', text: ' 테마를 완성했어요' });
    expect(feedParts(feed({ kind: 'LEVEL_UP', level: 4 }), CATALOG, () => undefined, () => undefined)).toEqual({ strong: 'Lv.4', text: ' 달성' });
    expect(feedParts(feed({ kind: 'BADGE_EARNED', badgeId: 'first' }), CATALOG, () => undefined, () => '첫 발자국').prefix).toBe('뱃지 「첫 발자국」를 얻었어요');
  });

  it('영토 비교: 지도 색 클래스·지역 요약·앞섬 문구', () => {
    const classes = compareClasses({ me: { handle: 'kim', regionCount: 2 }, other: { handle: 'lee', regionCount: 2 }, mutual: true, onlyMine: ['KR-37430'], both: ['KR-11010'], onlyTheirs: ['KR-31370'], lead: 0 });
    expect(Object.fromEntries(classes)).toEqual({ 37430: 'vs-mine', 11010: 'vs-both', 31370: 'vs-theirs' });
    expect(regionNames(['KR-11010', 'KR-37430'], CATALOG)).toBe('서울 종로구, 경북 울릉군');
    expect(regionNames(Array.from({ length: 8 }, () => 'KR-11010'), CATALOG)).toMatch(/ 외 2곳$/);
    expect([leadText(2), leadText(-1), leadText(0)]).toEqual(['2곳 앞섬', '1곳 뒤짐', '동점']);
  });
});
