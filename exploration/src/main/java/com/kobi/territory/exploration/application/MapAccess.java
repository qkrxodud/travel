package com.kobi.territory.exploration.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.map.ExpeditionMap;
import com.kobi.territory.exploration.domain.map.ExpeditionMapRepository;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.explorer.Explorer;
import com.kobi.territory.exploration.domain.explorer.ExplorerRepository;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.map.MapSelector;
import org.springframework.stereotype.Component;

/** 탐험가·지도 조회(DB 접근). 멤버십 판정은 ExpeditionMap(Members)이 한다. */
@Component
public class MapAccess {

    private final ExplorerRepository explorers;
    private final ExpeditionMapRepository maps;

    public MapAccess(ExplorerRepository explorers, ExpeditionMapRepository maps) {
        this.explorers = explorers;
        this.maps = maps;
    }

    /**
     * 활성 탐험가 — 없거나 병합돼 비활성이면 404 EXPLORER_NOT_FOUND. READ_COMMITTED 커맨드가 territory 를 잠근 <b>뒤</b> 이것으로
     * 다시 확인하므로, 병합(로그인)이 커밋된 뒤의 체크인은 여기서 거절된다(병합 중 체크인 경합 — AccountService 참고).
     */
    public Explorer requireExplorer(ExplorerId id) {
        return explorers.findById(id).filter(Explorer::active).orElseThrow(ExplorationError.EXPLORER_NOT_FOUND::exception);
    }

    /**
     * 잠금 뒤 "아직 활성인가" 확인 — 탐험가 행 공유 잠금(FOR SHARE)으로 최신 상태를 읽는다. 체크인은 territory 잠금 뒤, 지도 커맨드는
     * 지도 잠금 뒤에 부른다. 병합(로그인, 탐험가 행 배타 잠금)과 직렬화돼 병합 뒤의 쓰기는 404, 병합 전 쓰기는 병합이 기다렸다 읽는다.
     */
    public Explorer requireActiveLocked(ExplorerId id) {
        return explorers.findLockedShared(id).filter(Explorer::active)
            .orElseThrow(ExplorationError.EXPLORER_NOT_FOUND::exception);
    }

    /**
     * 지도 커맨드용 멤버 확인 — 지도 애그리거트를 불러오지 않는다(이어지는 잠금 조회가 같은 트랜잭션에서 오래된 사본을 받지 않게,
     * QA P1-1). 탐험가 없음 404, 지도 없음 404, 멤버 아님 403.
     */
    public void requireMembership(ExplorerId explorerId, MapId mapId) {
        requireExplorer(explorerId);
        if (maps.isMember(mapId, explorerId)) return;
        throw maps.exists(mapId) ? ExplorationError.NOT_A_MEMBER.exception() : ExplorationError.MAP_NOT_FOUND.exception(mapId.value());
    }

    /** selector 가 가리키는 지도 id(생략이면 개인 지도) — 애그리거트를 불러오지 않는다. 개인 지도가 없으면 탐험가·지도 없음. */
    public MapId mapIdOf(ExplorerId explorerId, MapSelector selector) {
        return selector.explicit().or(() -> maps.personalMapIdOf(explorerId)).orElseThrow(() -> {
            requireExplorer(explorerId);
            return selector.notFound();
        });
    }

    public MapMembership resolve(ExplorerId explorerId, MapSelector selector) {
        requireExplorer(explorerId);
        ExpeditionMap map = maps.find(selector, explorerId).orElseThrow(selector::notFound);
        return new MapMembership(map, map.requireMember(explorerId));
    }
}
