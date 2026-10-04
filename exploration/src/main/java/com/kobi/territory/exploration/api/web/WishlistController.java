package com.kobi.territory.exploration.api.web;

import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.catalog.api.query.RewardCalculator;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.web.WishlistDtos.WishStatusResponse;
import com.kobi.territory.exploration.api.web.WishlistDtos.WishlistResponse;
import com.kobi.territory.exploration.application.WishlistService;
import com.kobi.territory.exploration.domain.wishlist.Wishlist;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 가고 싶은 곳(9단계) — 아직 칠하지 않은 지역에 핀(비공개). 칠하면 다녀옴으로 바뀐다(체크인 소식으로 비동기). */
@RestController
@RequestMapping("/wishlist")
public class WishlistController {

    private final WishlistService wishlists;
    private final RegionCatalog catalog;
    private final RewardCalculator rewards;

    public WishlistController(WishlistService wishlists, RegionCatalog catalog, RewardCalculator rewards) {
        this.wishlists = wishlists;
        this.catalog = catalog;
        this.rewards = rewards;
    }

    @GetMapping
    public WishlistResponse wishlist(@CurrentExplorer ExplorerId explorerId) {
        return response(wishlists.view(explorerId));
    }

    @GetMapping("/{code}")
    public WishStatusResponse wish(@CurrentExplorer ExplorerId explorerId, @PathVariable("code") String code) {
        RegionCode region = RegionCode.of(code);
        return WishStatusResponse.of(region, wishlists.view(explorerId).pins().find(region));
    }

    @PutMapping("/{code}")
    public WishlistResponse pin(@CurrentExplorer ExplorerId explorerId, @PathVariable("code") String code) {
        return response(wishlists.pin(explorerId, RegionCode.of(code)));
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unpin(@CurrentExplorer ExplorerId explorerId, @PathVariable("code") String code) {
        wishlists.unpin(explorerId, RegionCode.of(code));
    }

    private WishlistResponse response(Wishlist wishlist) {
        return WishlistResponse.of(wishlist, wishlists.settings().maxPins(), rewards.wishFulfilled().amount(), catalog);
    }
}
