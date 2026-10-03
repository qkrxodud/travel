package com.kobi.territory.catalog.infra.entity;

import com.kobi.territory.catalog.domain.mystery.MysteryWeek;
import com.kobi.territory.common.model.RegionCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** mystery_week — 한 주의 미스터리 지역 선택 기록(8단계 V6). PK = 그 주 월요일. 한 번 넣으면 바꾸지 않는다. */
@Entity
@Table(name = "mystery_week")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MysteryWeekJpaEntity {

    @Id
    @Column(name = "week_start")
    private LocalDate weekStart;

    @Column(name = "region_code", nullable = false, length = 10)
    private String regionCode;

    @Column(name = "selected_at", nullable = false)
    private Instant selectedAt;

    public static MysteryWeekJpaEntity from(MysteryWeek week) {
        MysteryWeekJpaEntity entity = new MysteryWeekJpaEntity();
        entity.weekStart = week.weekStart();
        entity.regionCode = week.region().value();
        entity.selectedAt = week.selectedAt();
        return entity;
    }

    public MysteryWeek toDomain() {
        return new MysteryWeek(weekStart, RegionCode.of(regionCode), selectedAt);
    }
}
