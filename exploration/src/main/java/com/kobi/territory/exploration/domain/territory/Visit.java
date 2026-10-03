package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.Objects;

/**
 * Territory 내부 엔티티. (지도, 지역, 멤버) 단위로 한 건. 식별은 (region.code, checkedInBy).
 * visitedAt 은 처리 시각(서버 시계), visitDate 는 사용자가 적은 기록용 날짜다.
 * <ul>
 *   <li>generation — 같은 (지도, 지역, 멤버)의 체크인 회차(1부터, 결정 6). 취소 후 다시 칠하면 +1</li>
 *   <li>claimRankAt — 선점(지도 내 최초 체크인) 순서 기준. 평소 visitedAt 과 같고, 재가입으로 복구된 방문은 복구 시각이다
 *       (탈퇴 때 넘어간 선점이 돌아오지 않게 — §2-9)</li>
 *   <li>hiddenAt — 탈퇴 유예 중 숨김(지도·집계에서 빠진다). 재가입이면 복구, 유예가 끝나면 하드 삭제</li>
 *   <li>disputed — 지도장 이의(지도 내 랭킹 집계 제외용, 5단계)</li>
 * </ul>
 */
public final class Visit {

    private final RegionSnapshot region;
    private final ExplorerId checkedInBy;
    private final Verification verification;
    private final Instant visitedAt;
    private final int generation;
    private VisitDate visitDate;
    private Memo memo;
    private PhotoRef photo;
    private Instant claimRankAt;
    private Instant hiddenAt;
    private boolean disputed;

    /** 새 방문(1회차, 숨김·이의 없음). */
    public Visit(RegionSnapshot region, ExplorerId checkedInBy, VisitDate visitDate, Memo memo, PhotoRef photo,
                 Verification verification, Instant visitedAt) {
        this(region, checkedInBy, visitDate, memo, photo, verification, visitedAt, 1, visitedAt, null, false);
    }

    private Visit(RegionSnapshot region, ExplorerId checkedInBy, VisitDate visitDate, Memo memo, PhotoRef photo,
                  Verification verification, Instant visitedAt, int generation, Instant claimRankAt, Instant hiddenAt,
                  boolean disputed) {
        this.region = Objects.requireNonNull(region, "region");
        this.checkedInBy = Objects.requireNonNull(checkedInBy, "checkedInBy");
        this.visitDate = Objects.requireNonNull(visitDate, "visitDate");
        this.memo = memo == null ? Memo.EMPTY : memo;
        this.photo = photo;
        this.verification = Objects.requireNonNull(verification, "verification");
        this.visitedAt = Objects.requireNonNull(visitedAt, "visitedAt");
        if (generation < 1) throw new IllegalArgumentException("generation >= 1: " + generation);
        this.generation = generation;
        this.claimRankAt = Objects.requireNonNull(claimRankAt, "claimRankAt");
        this.hiddenAt = hiddenAt;
        this.disputed = disputed;
    }

    /** 저장소 복원. */
    public static Visit restore(RegionSnapshot region, ExplorerId checkedInBy, VisitDate visitDate, Memo memo, PhotoRef photo,
                                Verification verification, Instant visitedAt, int generation, Instant claimRankAt,
                                Instant hiddenAt, boolean disputed) {
        return new Visit(region, checkedInBy, visitDate, memo, photo, verification, visitedAt, generation, claimRankAt,
            hiddenAt, disputed);
    }

    /** generation 회차의 새 방문. */
    static Visit checkedIn(RegionSnapshot region, ExplorerId member, VisitDate date, Memo memo, PhotoRef photo, Instant at,
                           int generation) {
        return new Visit(region, member, date, memo, photo, Verification.NONE, at, generation, at, null, false);
    }

    /**
     * 병합(claimExplorer): 이 방문을 member 의 방문으로 옮긴 사본. 방문일·메모·사진·검증·처리 시각은 그대로, 회차는 member 의 다음 회차,
     * 선점 순서는 처리 시각, 숨김·이의는 없음.
     */
    Visit reassignedTo(ExplorerId member, int nextGeneration) {
        return new Visit(region, member, visitDate, memo, photo, verification, visitedAt, nextGeneration, visitedAt, null, false);
    }

    /**
     * 병합(공유 지도 재귀속, Q2): 이 방문을 member 의 방문으로 바꾼 사본. 선점 순서(claimRankAt)·처리 시각·방문일·메모·사진·숨김·이의를
     * 그대로 두어 지도 안 순위가 바뀌지 않는다. 회차만 member 의 다음 회차(회차는 (지도, 지역, 멤버)별로 단조 증가).
     */
    Visit reassignedKeepingRank(ExplorerId member, int nextGeneration) {
        return new Visit(region, member, visitDate, memo, photo, verification, visitedAt, nextGeneration, claimRankAt, hiddenAt,
            disputed);
    }

    /** 선점 순서(claimRankAt, 같으면 처리 시각)가 other 보다 앞서는지 — 공유 지도 재귀속 충돌 시 앞선 쪽을 남긴다. */
    boolean claimsBefore(Visit other) {
        int byRank = claimRankAt.compareTo(other.claimRankAt);
        return byRank != 0 ? byRank < 0 : visitedAt.isBefore(other.visitedAt);
    }

    /** 방문일이 other 보다 이른지(같으면 false) — 병합 충돌 시 이른 쪽을 남긴다. */
    boolean earlierThan(Visit other) {
        return visitDate.value().isBefore(other.visitDate.value());
    }

    void edit(VisitDate visitDate, Memo memo, PhotoRef photo) {
        this.visitDate = Objects.requireNonNull(visitDate, "visitDate");
        this.memo = memo == null ? Memo.EMPTY : memo;
        this.photo = photo;
    }

    void hide(Instant at) {
        this.hiddenAt = Objects.requireNonNull(at, "at");
    }

    /** 숨김을 푼다. 선점 순서는 복구 시각으로 — 넘어간 선점이 돌아오지 않는다. */
    void restoreAt(Instant at) {
        this.hiddenAt = null;
        this.claimRankAt = Objects.requireNonNull(at, "at");
    }

    void dispute(boolean flag) {
        this.disputed = flag;
    }

    boolean is(RegionCode code, ExplorerId member) {
        return region.code().equals(code) && checkedInBy.equals(member);
    }

    public boolean hidden() {
        return hiddenAt != null;
    }

    public RegionSnapshot region() { return region; }
    public RegionCode regionCode() { return region.code(); }
    public ExplorerId checkedInBy() { return checkedInBy; }
    public VisitDate visitDate() { return visitDate; }
    public Memo memo() { return memo; }
    public PhotoRef photo() { return photo; }
    public Verification verification() { return verification; }
    public Instant visitedAt() { return visitedAt; }
    public int generation() { return generation; }
    public Instant claimRankAt() { return claimRankAt; }
    public Instant hiddenAt() { return hiddenAt; }
    public boolean disputed() { return disputed; }
}
