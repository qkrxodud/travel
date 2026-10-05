package com.kobi.territory.catalog.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** tourapi_usage — TourAPI 하루 호출 수(13s단계 V11, 서울 날짜). 늘리기는 조건부 UPDATE 한 문장(TourApiUsageJpaRepository#increment). */
@Entity
@Table(name = "tourapi_usage")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourApiUsageJpaEntity {

    @Id
    @Column(name = "usage_date")
    private LocalDate usageDate;

    @Column(name = "calls", nullable = false)
    private int calls;

    public int calls() {
        return calls;
    }
}
