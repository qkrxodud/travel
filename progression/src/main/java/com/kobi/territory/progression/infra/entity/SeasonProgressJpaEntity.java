package com.kobi.territory.progression.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.domain.collectionbook.SeasonMark;
import com.kobi.territory.progression.domain.collectionbook.SeasonProgress;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * season_progress — 도감(CollectionBook, 지도 단위)의 계절 회차 하나(SeasonProgress, 9단계). marks = 회차 기간 안에 센 방문
 * "지역|멤버" 쉼표 구분, completed_member_ids = 완성 시점 멤버(수령자). 회차가 끝나도 지우지 않는다(닫힌 기록).
 */
@Entity
@Table(name = "season_progress")
@IdClass(SeasonProgressJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonProgressJpaEntity {

    @Id
    @Column(name = "map_id", length = 36)
    private String mapId;

    @Id
    @Column(name = "round_id", length = 20)
    private String roundId;

    @Column(name = "marks", nullable = false, length = 8000)
    private String marks;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_member_ids", length = 400)
    private String completedMemberIds;

    /** 낙관적 락 — 재계산과 도감 이벤트 처리의 동시 갱신을 충돌로 드러낸다(set_progress 와 같다). */
    @Version
    private Long version;

    public static SeasonProgressJpaEntity from(String mapId, SeasonProgress seasonProgress) {
        SeasonProgressJpaEntity entity = new SeasonProgressJpaEntity();
        entity.mapId = mapId;
        entity.roundId = seasonProgress.roundId();
        entity.apply(seasonProgress);
        return entity;
    }

    public void apply(SeasonProgress seasonProgress) {
        this.marks = CsvColumn.write(seasonProgress.marks().stream().map(SeasonMark::key).toList());
        this.completedAt = seasonProgress.completedAt();
        this.completedMemberIds = CsvColumn.write(seasonProgress.completedMembers().stream().map(ExplorerId::value).toList());
    }

    public String roundId() {
        return roundId;
    }

    public SeasonProgress toDomain() {
        return SeasonProgress.restore(roundId, CsvColumn.read(marks).stream().map(SeasonMark::parse).collect(Collectors.toSet()),
            completedAt, CsvColumn.read(completedMemberIds).stream().map(ExplorerId::of).collect(Collectors.toSet()));
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String mapId;
        private String roundId;
    }
}
