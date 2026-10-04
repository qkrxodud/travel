package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.wishlist.WishPin;
import com.kobi.territory.exploration.domain.wishlist.Wishlist;
import com.kobi.territory.exploration.domain.wishlist.WishlistRepository;
import com.kobi.territory.exploration.infra.entity.WishPinJpaEntity;
import com.kobi.territory.exploration.infra.entity.WishlistJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** 가고 싶은 곳 저장소 어댑터 — wishlist(루트) + wish_pin. 행 ↔ 도메인 변환은 엔티티가 한다. */
@Repository
class JpaWishlistRepository implements WishlistRepository {

    private final WishlistJpaRepository roots;
    private final WishPinJpaRepository pinRows;

    JpaWishlistRepository(WishlistJpaRepository roots, WishPinJpaRepository pinRows) {
        this.roots = roots;
        this.pinRows = pinRows;
    }

    /** 없을 때만 넣는다(동시에 넣어 생기는 유일성 위반·교착은 호출자가 목표 상태로 흡수한다). */
    @Override
    public void ensure(ExplorerId explorerId, Instant at) {
        roots.insertIfAbsent(explorerId.value(), at);
    }

    @Override
    public Optional<Wishlist> findLocked(ExplorerId explorerId) {
        return roots.lockById(explorerId.value()).map(root -> root.toDomain(pinRows.findByExplorerId(explorerId.value())));
    }

    @Override
    public Wishlist load(ExplorerId explorerId) {
        return roots.findById(explorerId.value()).map(root -> root.toDomain(pinRows.findByExplorerId(explorerId.value())))
            .orElseGet(() -> Wishlist.empty(explorerId));
    }

    /** 바뀐 핀은 넣거나 고치고, 뺀 핀은 지운다. */
    @Override
    public void save(Wishlist wishlist) {
        String explorerId = wishlist.explorerId().value();
        Map<String, WishPinJpaEntity> saved = pinRows.findByExplorerId(explorerId).stream()
            .collect(Collectors.toMap(WishPinJpaEntity::regionCode, Function.identity()));
        for (WishPin pin : wishlist.pins().changed()) {
            WishPinJpaEntity row = saved.get(pin.region().value());
            if (row == null) pinRows.save(WishPinJpaEntity.from(wishlist.explorerId(), pin));
            else row.apply(pin);
        }
        List<WishPinJpaEntity> gone = wishlist.pins().removed().stream().map(region -> saved.get(region.value()))
            .filter(row -> row != null).toList();
        pinRows.deleteAll(gone);
        pinRows.flush();
    }
}
