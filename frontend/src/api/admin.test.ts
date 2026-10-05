/**
 * 관리자 요청 — 관리자 토큰을 실어 계절 회차 목록을 읽고, 후보를 새로 모으고(같은 날 캐시를 건너뛸 수도), 확정한다(13s단계).
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminApi } from './admin';
import { apiClient } from './client';

afterEach(() => vi.restoreAllMocks());

describe('계절 회차 관리 요청', () => {
  it('회차 목록과 회차 하나를 관리자 토큰으로 읽는다', async () => {
    const admin = vi.spyOn(apiClient, 'admin').mockResolvedValue({});
    await adminApi.seasonLineups('secret');
    await adminApi.seasonLineup('secret', 'spring-2027');
    expect(admin.mock.calls).toEqual([['GET', '/admin/seasons', 'secret'], ['GET', '/admin/seasons/spring-2027', 'secret']]);
  });

  it('새로 모을 때 같은 날 받아 둔 응답을 쓸지 건너뛸지 서버에 알린다', async () => {
    const admin = vi.spyOn(apiClient, 'admin').mockResolvedValue({});
    await adminApi.refreshSeasonLineup('secret', 'spring-2027', false);
    await adminApi.refreshSeasonLineup('secret', 'spring-2027', true);
    expect(admin.mock.calls).toEqual([
      ['POST', '/admin/seasons/spring-2027/refresh?fresh=false', 'secret'],
      ['POST', '/admin/seasons/spring-2027/refresh?fresh=true', 'secret'],
    ]);
  });

  it('후보 확정을 요청하고, 회차 id 에 주소 문자가 섞여도 경로를 벗어나지 않는다', async () => {
    const admin = vi.spyOn(apiClient, 'admin').mockResolvedValue({});
    await adminApi.confirmSeasonLineup('secret', 'spring-2027');
    await adminApi.seasonLineup('secret', '../metrics');
    expect(admin.mock.calls).toEqual([['PUT', '/admin/seasons/spring-2027/confirm', 'secret'], ['GET', '/admin/seasons/..%2Fmetrics', 'secret']]);
  });
});
