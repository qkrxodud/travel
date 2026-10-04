package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.progression.infra.entity.ThemeProgressJpaEntity;
import com.kobi.territory.progression.domain.collectionbook.CollectionBook;
import com.kobi.territory.progression.domain.collectionbook.CollectionBookRepository;
import com.kobi.territory.progression.domain.collectionbook.SeasonProgress;
import com.kobi.territory.progression.domain.collectionbook.ThemeProgress;
import com.kobi.territory.progression.infra.entity.SeasonProgressJpaEntity;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** CollectionBook(도감) 저장소 어댑터 — set_progress(+ 9단계 season_progress). 행 ↔ 도메인 변환은 ThemeProgressJpaEntity 가 한다. */
@Repository
class JpaCollectionBookRepository implements CollectionBookRepository {

    private final ThemeProgressJpaRepository themeRows;
    private final SeasonProgressJpaRepository seasonRows;

    JpaCollectionBookRepository(ThemeProgressJpaRepository themeRows, SeasonProgressJpaRepository seasonRows) {
        this.themeRows = themeRows;
        this.seasonRows = seasonRows;
    }

    @Override
    public CollectionBook load(String mapId) {
        return ThemeProgressJpaEntity.toCollectionBook(mapId, themeRows.findByMapId(mapId), seasonRows.findByMapId(mapId));
    }

    /** 변경 반영: 새 테마 행은 추가, 있던 행은 갱신. */
    @Override
    public void save(CollectionBook collectionBook) {
        Map<String, ThemeProgressJpaEntity> saved = themeRows.findByMapId(collectionBook.mapId()).stream()
            .collect(Collectors.toMap(ThemeProgressJpaEntity::themeId, Function.identity()));
        for (ThemeProgress themeProgress : collectionBook.themeProgresses()) {
            ThemeProgressJpaEntity themeRow = saved.get(themeProgress.themeId());
            if (themeRow == null) {
                themeRows.save(ThemeProgressJpaEntity.from(collectionBook.mapId(), themeProgress));
            } else {
                themeRow.apply(themeProgress);
            }
        }
        Map<String, SeasonProgressJpaEntity> savedSeasons = seasonRows.findByMapId(collectionBook.mapId()).stream()
            .collect(Collectors.toMap(SeasonProgressJpaEntity::roundId, Function.identity()));
        for (SeasonProgress seasonProgress : collectionBook.seasonProgresses()) {
            SeasonProgressJpaEntity seasonRow = savedSeasons.get(seasonProgress.roundId());
            if (seasonRow == null) {
                seasonRows.save(SeasonProgressJpaEntity.from(collectionBook.mapId(), seasonProgress));
            } else {
                seasonRow.apply(seasonProgress);
            }
        }
    }

    /**
     * 재계산 결과로 바꾸기. 지우고 다시 넣지 않고 행마다 apply(없으면 추가)한 뒤 flush 한다 — 지우고 다시 넣으면 version 검사가
     * 사라져, 재계산이 읽은 뒤 커밋된 도감 이벤트 반영분을 오래된 내용으로 덮는다(구조 QA S2-1). apply 는 재계산이 처음
     * 읽은(같은 트랜잭션의 영속성 컨텍스트에 남은) 엔티티에 걸리므로 그 사이 바뀐 행이면 version 충돌 → 재계산 재시도.
     * 재계산 결과에 없는 행(재계산이 읽은 뒤 새로 생긴 테마 행)은 지우지 않는다 — rebuildBase 는 읽은 행을 모두 유지하므로
     * 결과에 없는 행은 동시에 생긴 행뿐이다.
     */
    @Override
    public void replace(CollectionBook collectionBook) {
        save(collectionBook);
        themeRows.flush();
        seasonRows.flush();
    }
}
