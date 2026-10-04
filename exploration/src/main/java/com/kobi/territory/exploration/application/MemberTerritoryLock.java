package com.kobi.territory.exploration.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.map.ExpeditionMapRepository;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.map.MapSelector;
import com.kobi.territory.exploration.domain.territory.TerritoryRepository;
import org.springframework.stereotype.Component;

/**
 * 영토 쓰기 커맨드의 잠금(체크인·수정·취소·이의, 9단계 재방문 도장). 요청한 탐험가가 멤버인 지도의 territory 행을 잠근다 — 멤버 확인을
 * 먼저(비멤버가 남의 지도 행을 잠그지 못하게, N2), 지도 행 공유 잠금 → territory 배타 잠금(QA P1-2) → 탐험가 행 공유 잠금(병합과 직렬화,
 * 4단계) → 잠금 뒤 멤버 재확인(그사이 커밋된 탈퇴면 403). 잠글 행이 없으면 원인(탐험가/지도 없음)을 낸다. 호출자 트랜잭션(READ_COMMITTED)
 * 안에서 부른다.
 */
@Component
public class MemberTerritoryLock {

    private final TerritoryRepository territories;
    private final ExpeditionMapRepository maps;
    private final MapAccess mapAccess;

    public MemberTerritoryLock(TerritoryRepository territories, ExpeditionMapRepository maps, MapAccess mapAccess) {
        this.territories = territories;
        this.maps = maps;
        this.mapAccess = mapAccess;
    }

    public MapMembership lockAsMember(ExplorerId explorerId, MapSelector selector) {
        MapId mapId = mapAccess.mapIdOf(explorerId, selector);
        mapAccess.requireMembership(explorerId, mapId);              // 비멤버는 잠그기 전에 거른다(N2)
        if (!maps.lockShared(mapId) || !territories.lock(mapId)) throw selector.notFound(); // 지도 S → territory X
        mapAccess.requireActiveLocked(explorerId);                   // → 탐험가 S: 그사이 커밋된 병합(로그인)이면 404(4단계)
        return mapAccess.resolve(explorerId, MapSelector.of(mapId)); // 잠금 뒤 다시 확인 — 그사이 커밋된 탈퇴면 403
    }
}
