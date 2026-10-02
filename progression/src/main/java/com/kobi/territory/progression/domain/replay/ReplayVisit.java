package com.kobi.territory.progression.domain.replay;

import com.kobi.territory.progression.domain.progress.ProgressVisit;
import com.kobi.territory.common.model.ExplorerId;
import java.util.Objects;

/** 재계산 재생용 체크인 한 건(탐험의 visitHistory → application 이 옮긴 값). 퀘스트 사실은 재생이 만든다. */
public record ReplayVisit(ExplorerId explorer, ProgressVisit visit) {
    public ReplayVisit {
        Objects.requireNonNull(explorer, "explorer");
        Objects.requireNonNull(visit, "visit");
    }
}
