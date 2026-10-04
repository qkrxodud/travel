package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.revisit.StampBook;
import com.kobi.territory.exploration.domain.revisit.StampBookRepository;
import com.kobi.territory.exploration.infra.entity.RevisitStampJpaEntity;
import org.springframework.stereotype.Repository;

/** 재방문 도장첩 저장소 어댑터 — revisit_stamp. 행 ↔ 도메인 변환은 RevisitStampJpaEntity 가 한다. */
@Repository
class JpaStampBookRepository implements StampBookRepository {

    private final RevisitStampJpaRepository stampRows;

    JpaStampBookRepository(RevisitStampJpaRepository stampRows) {
        this.stampRows = stampRows;
    }

    @Override
    public StampBook load(ExplorerId explorerId) {
        return RevisitStampJpaEntity.toStampBook(explorerId, stampRows.findByExplorerId(explorerId.value()));
    }

    /** 새 도장만 넣는다(도장은 지우거나 고치지 않는다). 같은 (지역, 연도)가 동시에 들어오면 PK 가 하나만 남긴다. */
    @Override
    public void save(StampBook stampBook) {
        stampBook.stamps().added().forEach(stamp -> stampRows.save(RevisitStampJpaEntity.from(stampBook.explorerId(), stamp)));
        stampRows.flush();
    }
}
