package com.kobi.territory.sharing.domain.card;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Objects;

/**
 * 카드 식별(§4 share_card PK(explorer_id, map_id, kind)). 지도는 그 탐험가의 개인 지도(4단계 — 공유 지도 카드는 범위 밖).
 * VS 카드는 저장하지 않는다(공개 쌍마다 행·파일이 늘지 않게 — 메모리 캐시, QA P3-9).
 */
public record ShareCardId(ExplorerId explorerId, String mapId, CardKind kind) {
    public ShareCardId {
        Objects.requireNonNull(explorerId, "explorerId");
        Objects.requireNonNull(mapId, "mapId");
        Objects.requireNonNull(kind, "kind");
        if (kind == CardKind.VS) throw new IllegalArgumentException("VS 카드는 저장하지 않는다");
    }

    /** 저장소 이미지 키 — 기준 해시를 넣는다: 서로 다른 기준의 동시 렌더가 같은 파일을 덮지 않는다(QA P3-4). */
    public String imageKey(CardBasis basis) {
        return explorerId.value() + "/" + kind.pathValue() + "-" + basis.shortHash() + ".png";
    }
}
