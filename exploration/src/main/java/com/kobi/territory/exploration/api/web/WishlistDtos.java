package com.kobi.territory.exploration.api.web;

import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RegionView;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.wishlist.WishPin;
import com.kobi.territory.exploration.domain.wishlist.Wishlist;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 가고 싶은 곳 API 응답 DTO(9단계). 위시리스트는 비공개 — 본인 API 만 있다. */
public final class WishlistDtos {

    private WishlistDtos() {}

    static final String WANTED = "WANTED";
    static final String VISITED = "VISITED";

    /** @param status WANTED(아직 안 감) | VISITED(다녀옴), @param fulfilledAt 다녀온 시각(아직이면 null) */
    public record WishItem(String regionCode, String regionName, String provinceCode, String status, Instant pinnedAt,
                           Instant fulfilledAt) {
        static WishItem of(WishPin pin, RegionCatalog catalog) {
            RegionView region = catalog.findRegion(pin.region()).orElse(null);
            return new WishItem(pin.region().value(), region == null ? null : region.name(),
                region == null ? null : region.provinceCode(), pin.fulfilled() ? VISITED : WANTED, pin.pinnedAt(), pin.fulfilledAt());
        }
    }

    /**
     * GET /wishlist · PUT /wishlist/{code} 응답.
     *
     * @param max          아직 다녀오지 않은 핀의 최대 수(설정값 — 화면에 박지 말 것)
     * @param pendingCount 아직 다녀오지 않은 핀 수(max 와 같으면 더 꽂을 수 없다)
     * @param xpPerWish    다녀오면 받는 XP(지역당 한 번)
     * @param items        최근에 꽂은 순
     */
    public record WishlistResponse(int max, int pendingCount, int fulfilledCount, int xpPerWish, List<WishItem> items) {
        static WishlistResponse of(Wishlist wishlist, int max, int xpPerWish, RegionCatalog catalog) {
            return new WishlistResponse(max, wishlist.pins().pendingCount(), wishlist.pins().fulfilledCount(), xpPerWish,
                wishlist.pins().newestFirst().stream().map(pin -> WishItem.of(pin, catalog)).toList());
        }
    }

    /** GET /wishlist/{code} — 핀이 없으면 pinned=false, 나머지 null. */
    public record WishStatusResponse(String regionCode, boolean pinned, String status, Instant pinnedAt, Instant fulfilledAt) {
        static WishStatusResponse of(RegionCode code, Optional<WishPin> pin) {
            return new WishStatusResponse(code.value(), pin.isPresent(), pin.map(found -> found.fulfilled() ? VISITED : WANTED)
                .orElse(null), pin.map(WishPin::pinnedAt).orElse(null), pin.map(WishPin::fulfilledAt).orElse(null));
        }
    }
}
