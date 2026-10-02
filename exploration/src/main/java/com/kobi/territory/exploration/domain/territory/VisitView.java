package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;

/**
 * 누군가(viewer)가 보는 방문 한 건. 다른 멤버 방문의 메모·사진은 비운다(§7 프라이버시 — 공유 지도에서 색칠과 집계만 공개).
 *
 * @param claim 그 지역의 선점(지역 색 = 선점자 색)인지
 */
public record VisitView(RegionSnapshot region, ExplorerId checkedInBy, VisitDate visitDate, Memo memo, PhotoRef photo,
                        Verification verification, Instant visitedAt, boolean claim, boolean disputed, int generation) {

    static VisitView of(Visit visit, ExplorerId viewer, boolean claim) {
        boolean mine = visit.checkedInBy().equals(viewer);
        return new VisitView(visit.region(), visit.checkedInBy(), visit.visitDate(), mine ? visit.memo() : Memo.EMPTY,
            mine ? visit.photo() : null, visit.verification(), visit.visitedAt(), claim, visit.disputed(), visit.generation());
    }
}
