package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Objects;

/**
 * Territory 내부 엔티티. (지도, 지역, 멤버) 단위로 한 건. 식별은 (region.code, checkedInBy).
 * visitedAt 은 처리 시각(서버 시계), visitDate 는 사용자가 적은 기록용 날짜다.
 */
public final class Visit {

    private final RegionSnapshot region;
    private final ExplorerId checkedInBy;
    private final Verification verification;
    private final Instant visitedAt;
    private VisitDate visitDate;
    private Memo memo;
    private PhotoRef photo;

    public Visit(RegionSnapshot region, ExplorerId checkedInBy, VisitDate visitDate, Memo memo, PhotoRef photo,
                 Verification verification, Instant visitedAt) {
        this.region = Objects.requireNonNull(region, "region");
        this.checkedInBy = Objects.requireNonNull(checkedInBy, "checkedInBy");
        this.visitDate = Objects.requireNonNull(visitDate, "visitDate");
        this.memo = memo == null ? Memo.EMPTY : memo;
        this.photo = photo;
        this.verification = Objects.requireNonNull(verification, "verification");
        this.visitedAt = Objects.requireNonNull(visitedAt, "visitedAt");
    }

    void edit(VisitDate visitDate, Memo memo, PhotoRef photo) {
        this.visitDate = Objects.requireNonNull(visitDate, "visitDate");
        this.memo = memo == null ? Memo.EMPTY : memo;
        this.photo = photo;
    }

    boolean is(RegionCode code, ExplorerId member) {
        return region.code().equals(code) && checkedInBy.equals(member);
    }

    public RegionSnapshot region() { return region; }
    public RegionCode regionCode() { return region.code(); }
    public ExplorerId checkedInBy() { return checkedInBy; }
    public VisitDate visitDate() { return visitDate; }
    public Memo memo() { return memo; }
    public PhotoRef photo() { return photo; }
    public Verification verification() { return verification; }
    public Instant visitedAt() { return visitedAt; }
}
