package com.kobi.territory.sharing.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.sharing.domain.card.CardBasis;
import com.kobi.territory.sharing.domain.card.CardKind;
import com.kobi.territory.sharing.domain.card.ShareCard;
import com.kobi.territory.sharing.domain.card.ShareCardId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * share_card — 자랑 카드(ShareCard). PK(explorer_id, map_id, kind)(§4). summary_hash·rendered_handle = 그릴 때 기준(공개 요약 해시 +
 * 찍힌 handle — §4 초안의 scene_ver·visit_ver 대체, QA P2-1), image_key = 저장소 키(기준 해시 포함).
 */
@Entity
@Table(name = "share_card")
@IdClass(ShareCardJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareCardJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Id
    @Column(name = "map_id", length = 36)
    private String mapId;

    @Id
    @Column(name = "kind", length = 12)
    private String kind;

    @Column(name = "summary_hash", nullable = false, length = 64)
    private String summaryHash;

    @Column(name = "rendered_handle", length = 30)
    private String renderedHandle;

    @Column(name = "image_key", nullable = false, length = 200)
    private String imageKey;

    @Column(name = "rendered_at", nullable = false)
    private Instant renderedAt;

    @Version
    private Long version;

    public static ShareCardJpaEntity from(ShareCard card) {
        ShareCardJpaEntity entity = new ShareCardJpaEntity();
        ShareCardId id = card.id();
        entity.explorerId = id.explorerId().value();
        entity.mapId = id.mapId();
        entity.kind = id.kind().name();
        entity.apply(card);
        return entity;
    }

    public void apply(ShareCard card) {
        this.summaryHash = card.basis().summaryHash();
        this.renderedHandle = card.basis().handle();
        this.imageKey = card.imageKey();
        this.renderedAt = card.renderedAt();
    }

    public static Key keyOf(ShareCardId id) {
        return new Key(id.explorerId().value(), id.mapId(), id.kind().name());
    }

    public ShareCard toDomain() {
        return ShareCard.restore(new ShareCardId(ExplorerId.of(explorerId), mapId, CardKind.valueOf(kind)),
            new CardBasis(summaryHash, renderedHandle), imageKey, renderedAt);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String explorerId;
        private String mapId;
        private String kind;
    }
}
