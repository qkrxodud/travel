/** 소셜(5단계): 친구(팔로우 — 로그인 필요)·친구 소식·랭킹 두 층·상위 %·영토 비교 */
import { apiClient } from './client';
import type {
  CompareResponse, FeedResponse, FriendRankingResponse, FriendResponse, FriendsResponse, MapRankingResponse, PercentileResponse,
} from './types/social';

const handlePath = (handle: string) => encodeURIComponent(handle);

export const socialApi = {
  friends: () => apiClient.request<FriendsResponse>('GET', '/friends'),
  follow: (handle: string) => apiClient.request<FriendResponse>('POST', '/friends/' + handlePath(handle)),
  unfollow: (handle: string) => apiClient.request<null>('DELETE', '/friends/' + handlePath(handle)),
  feed: () => apiClient.request<FeedResponse>('GET', '/feed'),
  friendRanking: () => apiClient.request<FriendRankingResponse>('GET', '/rankings/friends'),
  mapRanking: (mapId: string) => apiClient.request<MapRankingResponse>('GET', '/rankings/maps/' + encodeURIComponent(mapId)),
  percentile: () => apiClient.request<PercentileResponse>('GET', '/rankings/me/percentile'),
  compare: (handle: string) => apiClient.request<CompareResponse>('GET', '/compare/' + handlePath(handle)),
};
