/**
 * 서비스워커 규칙 — 해시가 붙은 정적 자산만 캐시하고 화면·API·관리자 경로는 늘 서버에서 받는다. 받은 푸시는 반드시 알림으로 보이고,
 * 누르면 같은 사이트의 그 화면으로 연다.
 */
import { describe, expect, it } from 'vitest';
import { cacheName, clickTarget, isCacheable, notificationFrom, parsePayload, pickWindow, precacheList, staleCaches } from './swRules';

const ORIGIN = 'https://territory.example';

describe('무엇을 캐시하는지', () => {
  it('빌드 산출물(/assets)·아이콘·매니페스트만 캐시에서 내준다', () => {
    expect(isCacheable('GET', ORIGIN + '/assets/index-Qs1ZzNSf.js', ORIGIN)).toBe(true);
    expect(isCacheable('GET', ORIGIN + '/icons/icon-192.png', ORIGIN)).toBe(true);
    expect(isCacheable('GET', ORIGIN + '/manifest.json', ORIGIN)).toBe(true);
  });

  it('화면·API·관리자·개발 경로는 캐시하지 않는다(새 배포·서버 값이 바로 보이게)', () => {
    for (const path of ['/', '/index.html', '/territory', '/progress', '/push/preferences', '/admin/metrics', '/dev/reset', '/events', '/u/kim', '/sw.js']) {
      expect(isCacheable('GET', ORIGIN + path, ORIGIN)).toBe(false);
    }
  });

  it('다른 사이트(글꼴 등)·주소에 값이 붙은 요청·GET 이 아닌 요청은 캐시하지 않는다', () => {
    expect(isCacheable('GET', 'https://fonts.gstatic.com/s/doHyeon.woff2', ORIGIN)).toBe(false);
    expect(isCacheable('GET', ORIGIN + '/assets/index.js?v=1', ORIGIN)).toBe(false);
    expect(isCacheable('POST', ORIGIN + '/assets/index.js', ORIGIN)).toBe(false);
  });

  it('미리 받을 목록에서도 같은 규칙으로 화면 파일을 뺀다', () => {
    expect(precacheList(['/assets/a-1.js', 'index.html', '/icons/icon-32.png', '/sw.js'], ORIGIN)).toEqual(['/assets/a-1.js', '/icons/icon-32.png']);
  });

  it('새 빌드가 활성화되면 지난 빌드 캐시만 지운다(다른 캐시는 건드리지 않는다)', () => {
    expect(cacheName('b2')).toBe('territory-static-b2');
    expect(staleCaches(['territory-static-b1', 'territory-static-b2', 'other-cache'], 'b2')).toEqual(['territory-static-b1']);
  });
});

describe('받은 푸시', () => {
  it('서버 본문의 제목·내용·묶음 표시로 알림을 띄우고, 누를 때 열 주소와 종류를 알림에 싣는다', () => {
    const spec = notificationFrom(parsePayload(JSON.stringify({
      kind: 'mystery', title: '❓ 이번 주 미스터리 지역이 정해졌어요', body: '어디인지는 비밀', url: '/?from=push&push=mystery#map', tag: 'mystery-2026-10-05',
    })), ORIGIN);
    expect(spec.title).toBe('❓ 이번 주 미스터리 지역이 정해졌어요');
    expect(spec.options).toMatchObject({
      body: '어디인지는 비밀', tag: 'mystery-2026-10-05', icon: '/icons/icon-192.png', badge: '/icons/badge-96.png',
      data: { url: ORIGIN + '/?from=push&push=mystery#map', kind: 'mystery' },
    });
  });

  it('본문이 깨져 있어도 알림은 띄운다(받은 푸시는 반드시 보인다는 약속) — 누르면 첫 화면', () => {
    const spec = notificationFrom(parsePayload('not json'), ORIGIN);
    expect(spec.title).toBe('나의 영토');
    expect(spec.options.data).toEqual({ url: ORIGIN + '/', kind: null });
  });
});

describe('알림을 누르면', () => {
  it('같은 사이트 주소만 열고, 다른 사이트 주소는 첫 화면으로 바꾼다', () => {
    expect(clickTarget('/?from=push&push=season#sets', ORIGIN)).toBe(ORIGIN + '/?from=push&push=season#sets');
    expect(clickTarget('https://evil.example/phish', ORIGIN)).toBe(ORIGIN + '/');
    expect(clickTarget(undefined, ORIGIN)).toBe(ORIGIN + '/');
  });

  it('열려 있는 창 중 보고 있던 창 → 보이는 창 → 아무 창 순서로 고르고, 없으면 새 창을 연다', () => {
    const elsewhere = { url: 'https://other.example/', focused: true, visibilityState: 'visible' };
    const hiddenBag = { url: ORIGIN + '/#bag', focused: false, visibilityState: 'hidden' };
    const visibleMap = { url: ORIGIN + '/#map', focused: false, visibilityState: 'visible' };
    expect(pickWindow([elsewhere, hiddenBag, visibleMap], ORIGIN)).toBe(2);
    expect(pickWindow([hiddenBag], ORIGIN)).toBe(0);
    expect(pickWindow([{ ...hiddenBag, focused: true }, visibleMap], ORIGIN)).toBe(0);
    expect(pickWindow([elsewhere], ORIGIN)).toBe(-1);
  });
});
