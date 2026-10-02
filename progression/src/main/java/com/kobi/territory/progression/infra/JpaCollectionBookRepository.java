package com.kobi.territory.progression.infra;

import com.kobi.territory.progression.domain.CollectionBook;
import com.kobi.territory.progression.domain.CollectionBookRepository;
import com.kobi.territory.progression.domain.SetProgress;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** CollectionBook(도감) 저장소 어댑터 — set_progress. 행 ↔ 도메인 변환은 SetProgressJpaEntity 가 한다. */
@Repository
class JpaCollectionBookRepository implements CollectionBookRepository {

    private final SetProgressJpaRepository setRows;

    JpaCollectionBookRepository(SetProgressJpaRepository setRows) {
        this.setRows = setRows;
    }

    @Override
    public CollectionBook load(String mapId) {
        return SetProgressJpaEntity.toCollectionBook(mapId, setRows.findByMapId(mapId));
    }

    @Override
    public void save(CollectionBook collectionBook) {
        Map<String, SetProgressJpaEntity> stale = setRows.findByMapId(collectionBook.mapId()).stream()
            .collect(Collectors.toMap(SetProgressJpaEntity::setId, Function.identity()));
        for (SetProgress setProgress : collectionBook.rows()) {
            SetProgressJpaEntity setRow = stale.remove(setProgress.setId());
            if (setRow == null) {
                setRows.save(SetProgressJpaEntity.from(collectionBook.mapId(), setProgress));
            } else {
                setRow.apply(setProgress);
            }
        }
        setRows.deleteAll(stale.values()); // 재계산이 비운 세트
    }
}
