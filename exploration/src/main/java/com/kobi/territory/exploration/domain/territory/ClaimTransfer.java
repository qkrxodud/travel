package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Objects;

/**
 * 선점 이전(§5) — 선점자의 방문이 취소되거나 탈퇴로 숨겨져, 그 지역을 다음 순서로 칠한 멤버가 선점자가 됐다.
 * application 이 ClaimTransferred(공개 이벤트)로 옮긴다.
 */
public record ClaimTransfer(RegionSnapshot region, ExplorerId from, ExplorerId to, Reason reason) {
    public ClaimTransfer {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(reason, "reason");
    }

    public enum Reason { CANCELLED, LEFT }
}
