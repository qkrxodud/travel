package com.kobi.territory.exploration.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.wishlist.WishPin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** wish_pin — 가고 싶은 곳 핀 하나(9단계, Wishlist 의 자식). fulfilled_at 이 있으면 "다녀옴". */
@Entity
@Table(name = "wish_pin")
@IdClass(WishPinJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishPinJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Id
    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(name = "pinned_at", nullable = false)
    private Instant pinnedAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    public static WishPinJpaEntity from(ExplorerId explorerId, WishPin pin) {
        WishPinJpaEntity entity = new WishPinJpaEntity();
        entity.explorerId = explorerId.value();
        entity.regionCode = pin.region().value();
        entity.apply(pin);
        return entity;
    }

    public void apply(WishPin pin) {
        this.pinnedAt = pin.pinnedAt();
        this.fulfilledAt = pin.fulfilledAt();
    }

    public String regionCode() {
        return regionCode;
    }

    public WishPin toDomain() {
        return WishPin.restore(RegionCode.of(regionCode), pinnedAt, fulfilledAt);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String explorerId;
        private String regionCode;
    }
}
