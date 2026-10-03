package com.kobi.territory.social.domain.ranking;

import com.kobi.territory.common.model.ExplorerId;

/** 친구 랭킹 한 줄. rank = 지역 수 기준 경쟁 순위(같은 지역 수면 같은 순위), me = 요청한 본인. */
public record FriendStanding(ExplorerId explorerId, int regionCount, int level, int rank, boolean me) {}
