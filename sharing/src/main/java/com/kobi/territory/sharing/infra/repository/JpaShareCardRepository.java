package com.kobi.territory.sharing.infra.repository;

import com.kobi.territory.sharing.domain.card.ShareCard;
import com.kobi.territory.sharing.domain.card.ShareCardId;
import com.kobi.territory.sharing.domain.card.ShareCardRepository;
import com.kobi.territory.sharing.infra.entity.ShareCardJpaEntity;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** ShareCard 저장소 어댑터 — 있으면 갱신, 없으면 넣기(동시 첫 렌더는 PK·version 충돌로 드러난다). */
@Repository
class JpaShareCardRepository implements ShareCardRepository {

    private final ShareCardJpaRepository rows;

    JpaShareCardRepository(ShareCardJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<ShareCard> find(ShareCardId id) {
        return rows.findById(ShareCardJpaEntity.keyOf(id)).map(ShareCardJpaEntity::toDomain);
    }

    @Override
    public void save(ShareCard card) {
        rows.findById(ShareCardJpaEntity.keyOf(card.id())).ifPresentOrElse(row -> row.apply(card),
            () -> rows.saveAndFlush(ShareCardJpaEntity.from(card)));
    }
}
