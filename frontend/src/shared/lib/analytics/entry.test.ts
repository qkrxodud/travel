/**
 * 첫 화면이 어디서 들어왔는지 — 카드·프로필 유입은 바이럴(K 계수) 근거라 갈래만 정확히 고르고, 주소의 개인 값은 쓰지 않는다.
 */
import { describe, expect, it } from 'vitest';
import { entryPoint, pushOpenKind, withoutEntryMark } from './entry';

const APP = 'https://territory.example/';

describe('들어온 갈래', () => {
  it('공유 링크에 붙인 표시가 있으면 그 갈래다', () => {
    expect(entryPoint(APP + '?from=card', '')).toBe('card');
    expect(entryPoint(APP + '?from=profile#map', '')).toBe('profile');
    expect(entryPoint(APP + '?from=invite', 'https://other.example/')).toBe('invite');
  });

  it('모르는 표시는 무시하고 다른 단서로 판단한다', () => {
    expect(entryPoint(APP + '?from=evil', '')).toBe('direct');
  });

  it('공개 프로필의 "이 지도에 합류"로 왔으면 프로필 유입이다', () => {
    expect(entryPoint(APP + '?joinProfile=kim&map=m1', '')).toBe('profile');
  });

  it('공개 프로필 페이지의 앱 링크(?from=profile)는 referrer 가 없어도 프로필 유입이다', () => {
    expect(entryPoint(APP + '?from=profile', '')).toBe('profile');
    expect(entryPoint(APP + '?joinProfile=kim&map=m1&from=profile', '')).toBe('profile');
  });

  it('같은 사이트의 공개 프로필에서 왔으면 프로필, 카드 이미지에서 왔으면 카드 유입이다', () => {
    expect(entryPoint(APP, 'https://territory.example/u/kim')).toBe('profile');
    expect(entryPoint(APP, 'https://territory.example/u/kim/card/territory.png')).toBe('card');
    expect(entryPoint(APP, 'https://territory.example/u/kim/vs/lee.png')).toBe('card');
  });

  it('같은 사이트의 다른 화면에서 왔거나 아무 단서가 없으면 직접 연 것이다', () => {
    expect(entryPoint(APP, 'https://territory.example/#/admin')).toBe('direct');
    expect(entryPoint(APP, '')).toBe('direct');
  });

  it('다른 사이트에서 왔으면 그 밖의 유입이다', () => {
    expect(entryPoint(APP, 'https://search.example/?q=영토')).toBe('other');
  });
});

describe('갈래 표시 지우기', () => {
  it('새로고침·즐겨찾기에 남지 않게 표시만 빼고 나머지 주소는 그대로 둔다', () => {
    expect(withoutEntryMark(APP + '?from=card&joinProfile=kim#map')).toBe('/?joinProfile=kim#map');
  });

  it('프로필의 합류 링크에서는 표시만 빼고 합류에 필요한 값은 남긴다', () => {
    expect(withoutEntryMark(APP + '?joinProfile=kim&map=m1&from=profile')).toBe('/?joinProfile=kim&map=m1');
  });

  it('표시가 없으면 바꾸지 않는다', () => {
    expect(withoutEntryMark(APP + '#map')).toBeNull();
  });
});

describe('알림을 눌러 열었을 때', () => {
  it('알림 주소로 열리면 알림 유입이고, 어떤 알림이었는지 안다', () => {
    expect(entryPoint(APP + '?from=push&push=mystery#map', '')).toBe('push');
    expect(pushOpenKind(APP + '?from=push&push=mystery#map')).toBe('mystery');
    expect(pushOpenKind(APP + '?from=push&push=season#sets')).toBe('season');
    expect(pushOpenKind(APP + '?from=push&push=streak#map')).toBe('streak');
  });

  it('모르는 알림 종류이거나 알림 유입이 아니면 알림 열기로 세지 않는다', () => {
    expect(pushOpenKind(APP + '?from=push&push=lottery')).toBeNull();
    expect(pushOpenKind(APP + '?from=card&push=mystery')).toBeNull();
    expect(pushOpenKind(APP + '#map')).toBeNull();
  });

  it('주소에서 알림 표시 두 개를 지우고 열 탭은 그대로 둔다', () => {
    expect(withoutEntryMark(APP + '?from=push&push=mystery#map')).toBe('/#map');
    expect(withoutEntryMark(APP + '?push=season#sets')).toBe('/#sets');
  });
});
