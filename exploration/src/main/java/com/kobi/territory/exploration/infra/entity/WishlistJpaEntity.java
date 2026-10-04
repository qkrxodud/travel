package com.kobi.territory.exploration.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.wishlist.WishPins;
import com.kobi.territory.exploration.domain.wishlist.Wishlist;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** wishlist — 가고 싶은 곳 루트 행(9단계). 핀 꽂기(상한)와 다녀옴 처리를 직렬화하는 잠금 대상. 자식 wish_pin 으로 애그리거트를 복원한다. */
@Entity
@Table(name = "wishlist")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishlistJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Wishlist toDomain(List<WishPinJpaEntity> pinRows) {
        return Wishlist.restore(ExplorerId.of(explorerId), WishPins.of(pinRows.stream().map(WishPinJpaEntity::toDomain).toList()));
    }
}
