package com.kobi.territory.exploration.domain.territory;

import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.map.MapSelector;
import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.Optional;

/**
 * Territory 저장소. Territory 루트 행(territory 테이블)이 지도 단위 직렬화 잠금 대상이다.
 * 커맨드(체크인·수정·취소)는 트랜잭션의 첫 문장으로 lock*을 호출한 뒤 load 해야 한다
 * (MySQL REPEATABLE READ에서 잠금 전 일반 SELECT가 스냅샷을 고정하면 동시 커밋을 못 본다).
 */
public interface TerritoryRepository {

    /** 지도 생성 시 빈 Territory 루트 행을 만든다. */
    void create(MapId mapId, Instant createdAt);

    /** 지도의 Territory 행을 배타 잠금(SELECT ... FOR UPDATE). 없으면 false. */
    boolean lock(MapId mapId);

    /** 탐험가 개인 지도의 Territory 행을 배타 잠금하고 그 mapId를 돌려준다. */
    Optional<MapId> lockPersonal(ExplorerId owner);

    /** selector 가 가리키는 지도의 Territory 행 잠금(생략이면 requester 의 개인 지도). */
    default Optional<MapId> lock(MapSelector selector, ExplorerId requester) {
        return selector.personal()
            ? lockPersonal(requester)
            : Optional.of(selector.mapId()).filter(this::lock);
    }

    /** 방문 로드(커맨드는 lock 이후에 호출). 방문이 없으면 빈 Territory. */
    Territory load(MapId mapId);

    void save(Territory territory);
}
