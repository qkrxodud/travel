package com.kobi.territory.social.domain.ranking;

import com.kobi.territory.common.model.ExplorerId;

/**
 * 지도 안 랭킹 한 줄(§5 — visit 을 (map_id, checked_in_by)로 센다, 이의 방문 제외).
 *
 * @param territories 영토 수(이의 아닌 보이는 방문)
 * @param claims      선점 수(그중 지금 선점인 방문)
 * @param legends     전설 지역 수
 * @param rank        순위(1부터, 세 값이 모두 같으면 같은 순위)
 */
public record MapStanding(ExplorerId explorerId, int territories, int claims, int legends, int rank) {}
