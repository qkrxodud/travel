package com.kobi.territory.exploration.infra;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.MapId;
import com.kobi.territory.exploration.domain.Memo;
import com.kobi.territory.exploration.domain.PhotoRef;
import com.kobi.territory.exploration.domain.RegionSnapshot;
import com.kobi.territory.exploration.domain.Verification;
import com.kobi.territory.exploration.domain.Visit;
import com.kobi.territory.exploration.domain.VisitDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** visit 테이블 ↔ Visit(Territory 의 자식). UQ(map_id, region_code, checked_in_by). 취소는 물리 삭제. */
@Entity
@Table(name = "visit")
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

    static VisitJpaEntity from(MapId mapId, Visit visit) {
        VisitJpaEntity entity = new VisitJpaEntity();
        entity.mapId = mapId.value();
        entity.regionCode = visit.regionCode().value();
        entity.checkedInBy = visit.checkedInBy().value();
        entity.verification = visit.verification().name();
        entity.visitedAt = visit.visitedAt();
        entity.apply(visit);
        return entity;
    }

    /** 수정 가능한 값(방문일·메모·사진)을 도메인 상태로 맞춘다. */
    void apply(Visit visit) {
        this.visitDate = visit.visitDate().value();
        this.memo = visit.memo().value();
        this.photoUrl = visit.photo() == null ? null : visit.photo().url();
    }

    RegionCode regionCode() {
        return RegionCode.of(regionCode);
    }

    /** (지역, 멤버) 식별 키 — 같은 지도 안에서 방문 하나를 가리킨다. */
    String identity() {
        return identity(regionCode, checkedInBy);
    }

    static String identity(Visit visit) {
        return identity(visit.regionCode().value(), visit.checkedInBy().value());
    }

    private static String identity(String regionCode, String explorerId) {
        return regionCode + "|" + explorerId;
    }

    /** 지역 정보(카탈로그 스냅샷)는 저장하지 않으므로 호출자가 넘긴다. */
    Visit toDomain(RegionSnapshot region) {
        return new Visit(region, ExplorerId.of(checkedInBy), VisitDate.of(visitDate), Memo.of(memo),
            PhotoRef.ofNullable(photoUrl), Verification.valueOf(verification), visitedAt);
    }
}
