/** 탐험(1단계)·공유 지도(3단계)·계정 handle(4단계). mapId 를 생략(null)하면 개인 지도. */
import { apiClient, toServerCode } from './client';
import type {
  CheckInResponse, HandleResponse, InviteCodeResponse, LeaveResponse, MapDetailResponse, MapSettings, MapSummaryResponse,
  PreviewResponse, TerritoryResponse,
} from './types/exploration';

/** 지금 보는 지도를 쿼리 문자열로 붙인다(null = 개인 지도). */
function withMap(path: string, mapId: string | null): string {
  if (!mapId) return path;
  return path + (path.includes('?') ? '&' : '?') + 'mapId=' + encodeURIComponent(mapId);
}

const mapPath = (mapId: string) => '/maps/' + encodeURIComponent(mapId);

export interface CheckInInput {
  code: string;
  visitDate: string;
  memo: string;
  photoUrl: string;
  mapId: string | null;
}

export const explorationApi = {
  territory: (mapId: string | null) => apiClient.request<TerritoryResponse>('GET', withMap('/territory', mapId)),
  preview: (code: string, mapId: string | null) =>
    apiClient.request<PreviewResponse>('GET', withMap('/visits/preview?region=' + encodeURIComponent(toServerCode(code)), mapId)),
  checkIn: ({ code, visitDate, memo, photoUrl, mapId }: CheckInInput) =>
    apiClient.request<CheckInResponse>('POST', '/visits', {
      regionCode: toServerCode(code), visitDate, memo, photoUrl: photoUrl || undefined, mapId: mapId || undefined,
    }),
  editVisit: (code: string, visitDate: string, memo: string, mapId: string | null) =>
    apiClient.request<unknown>('PATCH', withMap('/visits/' + toServerCode(code), mapId), { visitDate, memo }),
  cancelVisit: (code: string, mapId: string | null) => apiClient.request<unknown>('DELETE', withMap('/visits/' + toServerCode(code), mapId)),

  maps: () => apiClient.request<MapSummaryResponse[]>('GET', '/maps'),
  mapDetail: (mapId: string) => apiClient.request<MapDetailResponse>('GET', mapPath(mapId)),
  createMap: (name: string) => apiClient.request<MapDetailResponse>('POST', '/maps', { name }),
  joinMap: (inviteCode: string) => apiClient.request<MapDetailResponse>('POST', '/maps/join', { inviteCode }),
  joinViaProfile: (handle: string, mapId: string) =>
    apiClient.request<MapDetailResponse>('POST', '/maps/join-via-profile/' + encodeURIComponent(handle), { mapId }),
  leaveMap: (mapId: string) => apiClient.request<LeaveResponse>('POST', mapPath(mapId) + '/leave'),
  regenerateInvite: (mapId: string) => apiClient.request<InviteCodeResponse>('POST', mapPath(mapId) + '/invite-code'),
  saveSettings: (mapId: string, settings: MapSettings) => apiClient.request<MapDetailResponse>('PUT', mapPath(mapId) + '/settings', settings),
  dispute: (mapId: string, code: string, memberId: string, disputed: boolean) =>
    apiClient.request<unknown>('PUT', `${mapPath(mapId)}/visits/${toServerCode(code)}/${memberId}/dispute`, { disputed }),

  changeHandle: (handle: string) => apiClient.request<HandleResponse>('PUT', '/me/handle', { handle }),
};
