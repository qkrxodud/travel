package com.kobi.territory.exploration.domain.wishlist;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;

/** 핀이 "다녀옴"으로 바뀌었다 → application 이 공개 이벤트 WishFulfilled 로 적재한다. */
public record WishFulfillment(RegionCode region, Instant pinnedAt, Instant fulfilledAt) {}
