package com.kobi.territory.exploration.api.query;

import com.kobi.territory.common.model.Rarity;

/**
 * 지도 안 랭킹(5단계 소셜)용 방문 한 건(공개 Query DTO). 지도에 보이는 방문만(탈퇴 유예로 숨긴 방문 제외) — 메모·사진·날짜는 싣지 않는다.
 *
 * @param claimOrder 지역 안 선점 순서(1 = 지금 선점 방문, 2 = 다음 …, 선점 순서 기준) — 소셜은 이의 방문을 빼고 가장 앞선 방문을 선점으로 센다
 * @param disputed   지도장이 이의 표시했는지(지도 안 랭킹 집계에서 뺀다)
 */
public record MapVisitView(String explorerId, String regionCode, Rarity rarity, int claimOrder, boolean disputed) {}
