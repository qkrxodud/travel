package com.kobi.territory.exploration.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** visit 테이블. UQ(map_id, region_code, checked_in_by). 취소는 물리 삭제. */
@Entity
@Table(name = "visit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class VisitJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "map_id", nullable = false, length = 36)
    private String mapId;

    @Column(name = "region_code", nullable = false, length = 10)
    private String regionCode;

    @Column(name = "checked_in_by", nullable = false, length = 36)
    private String checkedInBy;

    @Column(nullable = false, length = 8)
    private String verification;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(nullable = false, length = 160)
    private String memo;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "visited_at", nullable = false)
    private Instant visitedAt;

    VisitJpaEntity(String mapId, String regionCode, String checkedInBy, String verification, LocalDate visitDate,
                   String memo, String photoUrl, Instant visitedAt) {
        this.mapId = mapId;
        this.regionCode = regionCode;
        this.checkedInBy = checkedInBy;
        this.verification = verification;
        this.visitDate = visitDate;
        this.memo = memo;
        this.photoUrl = photoUrl;
        this.visitedAt = visitedAt;
    }

    void update(LocalDate visitDate, String memo, String photoUrl) {
        this.visitDate = visitDate;
        this.memo = memo;
        this.photoUrl = photoUrl;
    }
}
