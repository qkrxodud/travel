package com.kobi.territory.exploration.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.revisit.RevisitStamp;
import com.kobi.territory.exploration.domain.revisit.RevisitStamps;
import com.kobi.territory.exploration.domain.revisit.StampBook;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * revisit_stamp — 재방문 도장 한 개(9단계, StampBook 의 자식). PK(explorer_id, region_code, stamp_year) 가 "지역·연도당 하나"를 지킨다.
 * 연도 컬럼은 예약어(YEAR)를 피해 stamp_year. 지우지 않는다.
 */
@Entity
@Table(name = "revisit_stamp")
@IdClass(RevisitStampJpaEntity.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevisitStampJpaEntity {

    @Id
    @Column(name = "explorer_id", length = 36)
    private String explorerId;

    @Id
    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Id
    @Column(name = "stamp_year")
    private int year;

    @Column(name = "stamped_at", nullable = false)
    private Instant stampedAt;

    public static RevisitStampJpaEntity from(ExplorerId explorerId, RevisitStamp stamp) {
        RevisitStampJpaEntity entity = new RevisitStampJpaEntity();
        entity.explorerId = explorerId.value();
        entity.regionCode = stamp.region().value();
        entity.year = stamp.year();
        entity.stampedAt = stamp.stampedAt();
        return entity;
    }

    public RevisitStamp toDomain() {
        return new RevisitStamp(RegionCode.of(regionCode), year, stampedAt);
    }

    /** 한 탐험가의 도장 행으로 도장첩을 복원한다. */
    public static StampBook toStampBook(ExplorerId explorerId, List<RevisitStampJpaEntity> rows) {
        return StampBook.restore(explorerId, RevisitStamps.of(rows.stream().map(RevisitStampJpaEntity::toDomain).toList()));
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String explorerId;
        private String regionCode;
        private int year;
    }
}
