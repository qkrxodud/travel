package com.kobi.territory.progression.infra;

import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.progression.domain.CollectionBook;
import com.kobi.territory.progression.domain.SetProgress;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** set_progress — 도감(CollectionBook, 지도 단위)의 세트 하나. collected_codes 는 쉼표 구분. */
@Entity
@Table(name = "set_progress")
@IdClass(SetProgressJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SetProgressJpaEntity {

    @Id
    @Column(name = "map_id", length = 36)
    private String mapId;

    @Id
    @Column(name = "set_id", length = 20)
    private String setId;

    @Column(name = "collected_codes", nullable = false, length = 1000)
    private String collectedCodes;

    @Column(name = "completed_at")
    private Instant completedAt;

    static SetProgressJpaEntity from(String mapId, SetProgress setProgress) {
        SetProgressJpaEntity entity = new SetProgressJpaEntity();
        entity.mapId = mapId;
        entity.setId = setProgress.setId();
        entity.apply(setProgress);
        return entity;
    }

    void apply(SetProgress setProgress) {
        this.collectedCodes = CsvColumn.write(setProgress.collected().stream().map(RegionCode::value).toList());
        this.completedAt = setProgress.completedAt();
    }

    String setId() {
        return setId;
    }

    SetProgress toDomain() {
        return new SetProgress(setId, CsvColumn.read(collectedCodes).stream().map(RegionCode::of).collect(Collectors.toSet()),
            completedAt);
    }

    /** 한 지도의 세트 행들로 도감 애그리거트를 복원한다. */
    static CollectionBook toCollectionBook(String mapId, List<SetProgressJpaEntity> rows) {
        return CollectionBook.restore(mapId, rows.stream().map(SetProgressJpaEntity::toDomain).toList());
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    static class Key implements Serializable {
        private String mapId;
        private String setId;
    }
}
