package com.kobi.territory.progression.infra;

import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.domain.CollectionBook;
import com.kobi.territory.progression.domain.CollectionBookRepository;
import com.kobi.territory.progression.domain.SetProgress;
import com.kobi.territory.progression.infra.ProgressJpaEntities.SetRow;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** CollectionBook(도감) ↔ set_progress(map_id, set_id, collected_codes, completed_at). collected_codes 는 쉼표 구분 문자열. */
@Repository
class JpaCollectionBookRepository implements CollectionBookRepository {

    private final SetRowRepository setRows;

    JpaCollectionBookRepository(SetRowRepository setRows) {
        this.setRows = setRows;
    }

    @Override
    public CollectionBook load(String mapId) {
        return CollectionBook.restore(mapId, setRows.findByMapId(mapId).stream()
            .map(setRow -> new SetProgress(setRow.getSetId(),
                JpaExplorerProgressRepository.splitSet(setRow.getCollectedCodes()).stream()
                    .map(RegionCode::of).collect(Collectors.toSet()),
                setRow.getCompletedAt()))
            .toList());
    }

    @Override
    public void save(CollectionBook collectionBook) {
        Map<String, SetRow> existing = setRows.findByMapId(collectionBook.mapId()).stream()
            .collect(Collectors.toMap(SetRow::getSetId, Function.identity()));
        for (SetProgress setProgress : collectionBook.rows()) {
            SetRow setRow = Optional.ofNullable(existing.remove(setProgress.setId()))
                .orElseGet(() -> new SetRow(collectionBook.mapId(), setProgress.setId()));
            setRow.setCollectedCodes(setProgress.collected().stream().map(RegionCode::value).sorted()
                .collect(Collectors.joining(",")));
            setRow.setCompletedAt(setProgress.completedAt());
            setRows.save(setRow);
        }
        setRows.deleteAll(existing.values()); // 재계산이 비운 세트
    }
}
