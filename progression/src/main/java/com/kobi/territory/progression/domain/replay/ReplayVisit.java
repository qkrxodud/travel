package com.kobi.territory.progression.domain.replay;

import com.kobi.territory.progression.domain.progress.ProgressVisit;
import com.kobi.territory.common.model.ExplorerId;
import java.util.List;
import java.util.Objects;

/**
 * 재계산 재생용 체크인 한 건(탐험의 visitHistory → application 이 옮긴 값). 퀘스트 사실은 재생이 만든다.
 *
 * @param members 지도 멤버(재계산에서 새로 생기는 테마 완성의 수령자 — 결정 1). 비었으면 칠한 사람만
 */
public record ReplayVisit(ExplorerId explorer, ProgressVisit visit, List<ExplorerId> members) {
    public ReplayVisit {
        Objects.requireNonNull(explorer, "explorer");
        Objects.requireNonNull(visit, "visit");
        members = List.copyOf(members);
    }

    public ReplayVisit(ExplorerId explorer, ProgressVisit visit) {
        this(explorer, visit, List.of(explorer));
    }
}
