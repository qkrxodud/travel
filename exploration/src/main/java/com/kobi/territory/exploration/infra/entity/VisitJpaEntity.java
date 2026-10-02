package com.kobi.territory.exploration.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.territory.Memo;
import com.kobi.territory.exploration.domain.territory.PhotoRef;
import com.kobi.territory.exploration.domain.territory.RegionSnapshot;
import com.kobi.territory.exploration.domain.territory.Verification;
import com.kobi.territory.exploration.domain.territory.Visit;
import com.kobi.territory.exploration.domain.territory.VisitDate;
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

/**
 * visit 테이블 ↔ Visit(Territory 의 자식). UQ(map_id, region_code, checked_in_by). 취소는 물리 삭제.
 * V3: generation(체크인 회차), claim_rank_at(선점 순서), hidden_at(탈퇴 유예 숨김), disputed(지도장 이의).
 */
@Entity
@Table(name = "visit")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitJpaEntity {

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

    @Column(nullable = false)
    private int generation;

    /** 선점 순서 기준(V3 이전 행은 NULL → visited_at). */
    @Column(name = "claim_rank_at")
    private Instant claimRankAt;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(nullable = false)
    private boolean disputed;

    public static VisitJpaEntity from(MapId mapId, Visit visit) {
        VisitJpaEntity entity = new VisitJpaEntity();
        entity.mapId = mapId.value();
        entity.regionCode = visit.regionCode().value();
        entity.checkedInBy = visit.checkedInBy().value();
        entity.verification = visit.verification().name();
        entity.visitedAt = visit.visitedAt();
        entity.generation = visit.generation();
        entity.apply(visit);
        return entity;
    }

    /** 바뀔 수 있는 값(방문일·메모·사진·선점 순서·숨김·이의)을 도메인 상태로 맞춘다. */
    public void apply(Visit visit) {
        this.visitDate = visit.visitDate().value();
        this.memo = visit.memo().value();
        this.photoUrl = visit.photo() == null ? null : visit.photo().url();
        this.claimRankAt = visit.claimRankAt();
        this.hiddenAt = visit.hiddenAt();
        this.disputed = visit.disputed();
    }

    public RegionCode regionCode() {
        return RegionCode.of(regionCode);
    }

    /** (지역, 멤버) 식별 키 — 같은 지도 안에서 방문 하나를 가리킨다. */
    public String identity() {
        return identity(regionCode, checkedInBy);
    }

    public static String identity(Visit visit) {
        return identity(visit.regionCode().value(), visit.checkedInBy().value());
    }

    private static String identity(String regionCode, String explorerId) {
        return regionCode + "|" + explorerId;
    }

    /** 지역 정보(카탈로그 스냅샷)는 저장하지 않으므로 호출자가 넘긴다. */
    public Visit toDomain(RegionSnapshot region) {
        return Visit.restore(region, ExplorerId.of(checkedInBy), VisitDate.of(visitDate), Memo.of(memo),
            PhotoRef.ofNullable(photoUrl), Verification.valueOf(verification), visitedAt, Math.max(generation, 1),
            claimRankAt == null ? visitedAt : claimRankAt, hiddenAt, disputed);
    }
}
