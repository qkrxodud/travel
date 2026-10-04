package com.kobi.territory.analytics.domain.tracking;

import com.kobi.territory.analytics.domain.actor.ActorKey;
import com.kobi.territory.analytics.domain.actor.Country;
import com.kobi.territory.analytics.domain.actor.DeviceType;
import com.kobi.territory.analytics.domain.actor.ExplorerHash;
import com.kobi.territory.analytics.domain.actor.VisitorId;
import java.time.Instant;
import java.util.Objects;

/**
 * 화면 이벤트 묶음 하나를 받은 상황 — 누가(방문·탐험가·셀 열쇠), 어떤 기기·나라에서, 언제 받았는지.
 *
 * @param explorerHash 토큰·세션으로 확인된 탐험가(없으면 null)
 * @param country      모르면 null
 */
public record ClientContext(VisitorId visitorId, ExplorerHash explorerHash, ActorKey actor, DeviceType device, Country country,
                            Instant receivedAt) {
    public ClientContext {
        Objects.requireNonNull(visitorId, "visitorId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }
}
