package com.kobi.territory.exploration.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.MemberJoined;
import com.kobi.territory.exploration.api.event.MemberLeft;
import com.kobi.territory.exploration.api.event.MemberPurged;
import com.kobi.territory.exploration.api.event.VisitsHidden;
import com.kobi.territory.exploration.api.event.VisitsRestored;
import com.kobi.territory.exploration.domain.map.ExpeditionMapRepository;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.territory.HideResult;
import com.kobi.territory.exploration.domain.territory.RestoreResult;
import com.kobi.territory.exploration.domain.territory.Territory;
import com.kobi.territory.exploration.domain.territory.TerritoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지도 멤버십 변화 → 영토(Territory) 반영(구독자 exploration.territory). 지도 커맨드와 다른 트랜잭션에서 territory 행을 잠근다
 * (§2-9 — 가입·탈퇴가 방문 잠금과 묶이지 않게). 숨김·복구·삭제 판단은 Territory 가 하고, 모두 멱등하다(재전달 안전).
 * 잠금이 첫 문장이다(READ_COMMITTED — CheckInService 와 같은 규칙).
 */
@Service
public class TerritoryMembershipService {

    static final String AGGREGATE = CheckInService.AGGREGATE;

    private final TerritoryRepository territories;
    private final ExpeditionMapRepository maps;
    private final EventOutbox outbox;
    private final Clock clock;

    public TerritoryMembershipService(TerritoryRepository territories, ExpeditionMapRepository maps, EventOutbox outbox,
                                      Clock clock) {
        this.territories = territories;
        this.maps = maps;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** 탈퇴 → 그 멤버 방문 숨김 + 선점 이전(ClaimTransferred). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onMemberLeft(MemberLeft event) {
        MapId mapId = MapId.of(event.mapId());
        if (!territories.lock(mapId)) return;
        Territory territory = territories.load(mapId);
        HideResult result = territory.hideMember(ExplorerId.of(event.explorerId()), event.leftAt());
        territories.save(territory);
        Instant now = clock.instant();
        result.claimTransfers().forEach(transfer ->
            outbox.append(AGGREGATE, mapId.value(), CheckInService.claimTransferred(mapId, transfer, now)));
        outbox.append(AGGREGATE, mapId.value(), new VisitsHidden(mapId.value(), event.explorerId(), codes(result.hidden()),
            codes(result.regionsGone()), event.leftAt()));
    }

    /** 유예 안 재가입 → 숨긴 방문 복구(선점은 돌아오지 않음). 새 합류는 영토에 할 일이 없다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onMemberJoined(MemberJoined event) {
        if (!event.rejoined()) return;
        MapId mapId = MapId.of(event.mapId());
        if (!territories.lock(mapId)) return;
        Territory territory = territories.load(mapId);
        RestoreResult result = territory.restoreMember(ExplorerId.of(event.explorerId()), event.joinedAt());
        territories.save(territory);
        List<String> memberIds = maps.findById(mapId).map(map -> map.memberIds().stream().map(ExplorerId::value).toList())
            .orElse(List.of());
        outbox.append(AGGREGATE, mapId.value(), new VisitsRestored(mapId.value(), event.explorerId(),
            codes(result.restored()), codes(result.regionsBack()), memberIds, event.joinedAt(),
            result.restored().stream().map(code -> new VisitsRestored.RestoredVisit(code.value(), result.visitedAt().get(code))).toList()));
    }

    /** 유예 끝 → 숨긴 방문 하드 삭제. 탐험가 단위 기록(explorer_region)은 그대로(§5) — 하류에 알릴 것이 없다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onMemberPurged(MemberPurged event) {
        MapId mapId = MapId.of(event.mapId());
        if (!territories.lock(mapId)) return;
        Territory territory = territories.load(mapId);
        territory.purgeHidden(ExplorerId.of(event.explorerId()));
        territories.save(territory);
    }

    private static List<String> codes(List<RegionCode> regions) {
        return regions.stream().map(RegionCode::value).toList();
    }
}
