package com.kobi.territory.exploration.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.event.RecalculationRequests;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.ExplorerMerged;
import com.kobi.territory.exploration.api.event.MemberJoined;
import com.kobi.territory.exploration.api.event.MemberPurged;
import com.kobi.territory.exploration.api.event.MemberReassigned;
import com.kobi.territory.exploration.api.event.VisitsMerged;
import com.kobi.territory.exploration.domain.map.ExpeditionMap;
import com.kobi.territory.exploration.domain.map.ExpeditionMapRepository;
import com.kobi.territory.exploration.domain.map.Handover;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.territory.AbsorbResult;
import com.kobi.territory.exploration.domain.territory.ReassignResult;
import com.kobi.territory.exploration.domain.territory.Territory;
import com.kobi.territory.exploration.domain.territory.TerritoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 병합(claimExplorer) 이벤트 연쇄 — 한 트랜잭션 한 애그리거트, 모두 멱등(재전달 안전).
 * <pre>
 * [로그인 tx — Explorer A]  A → MERGED, outbox: ExplorerMerged + 공유 지도마다 MembershipHandover
 * ExplorerMerged      → (exploration.territory)     B 개인 territory 잠금 → A 개인 지도 방문 흡수(이른 방문일 유지) → VisitsMerged + 재계산 예약(B)
 * VisitsMerged        → (exploration.territory)     A 개인 territory 잠금 → A 방문 정리(삭제)
 * MembershipHandover  → (exploration.expedition-map) 공유 지도 잠금 → A 자리를 B 가 잇거나(멤버 아님) A 만 빠짐
 *                         → MemberPurged?·MemberJoined?(세트 배경 — 꾸미기) + MemberReassigned(탈퇴 아님 — 선점을 남에게 넘기지 않음)
 * MemberReassigned    → (exploration.territory)     그 지도 territory 잠금 → A 방문을 선점 순서 그대로 B 것으로(충돌: 선점 순서 이른 쪽) + 재계산 예약(B)
 * 재계산 예약          → (app-api 배치) 보류 규칙(미전달 이벤트 없음) 만족 시 B 의 진행·인벤토리 재계산
 * </pre>
 * A 의 개인 지도 territory 는 병합 tx 이후 아무도 쓰지 않는다(체크인이 탐험가 행 공유 잠금으로 비활성을 보고 거절) — 그래서 흡수는 그 지도를
 * 잠그지 않고 읽는다.
 */
@Service
public class ExplorerMergeService {

    private static final Logger log = LoggerFactory.getLogger(ExplorerMergeService.class);
    static final String RECALCULATION_REASON = "EXPLORER_MERGED";

    private final TerritoryRepository territories;
    private final ExpeditionMapRepository maps;
    private final EventOutbox outbox;
    private final RecalculationRequests recalculations;
    private final ExplorationSettings settings;
    private final Clock clock;

    public ExplorerMergeService(TerritoryRepository territories, ExpeditionMapRepository maps, EventOutbox outbox,
                                RecalculationRequests recalculations, ExplorationSettings settings, Clock clock) {
        this.territories = territories;
        this.maps = maps;
        this.outbox = outbox;
        this.recalculations = recalculations;
        this.settings = settings;
        this.clock = clock;
    }

    /** A 개인 지도 방문 → B 개인 지도(B territory 만 고친다). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onExplorerMerged(ExplorerMerged event) {
        MapId intoMap = MapId.of(event.intoPersonalMapId());
        if (!territories.lock(intoMap)) return;
        Territory into = territories.load(intoMap);
        AbsorbResult result = into.absorb(territories.load(MapId.of(event.fromPersonalMapId())),
            ExplorerId.of(event.fromExplorerId()), ExplorerId.of(event.intoExplorerId()));
        territories.save(into);
        outbox.append(CheckInService.AGGREGATE, intoMap.value(), new VisitsMerged(intoMap.value(), event.intoExplorerId(),
            event.fromExplorerId(), codes(result.added()), codes(result.replaced()), event.mergedAt()));
        recalculations.request(event.intoExplorerId(), RECALCULATION_REASON, clock.instant());
    }

    /** 흡수가 끝난 뒤 A 개인 지도 방문 정리(A territory 만 고친다). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onVisitsMerged(VisitsMerged event) {
        maps.personalMapIdOf(ExplorerId.of(event.fromExplorerId())).filter(territories::lock).ifPresent(fromMap -> {
            Territory from = territories.load(fromMap);
            from.releaseMember(ExplorerId.of(event.fromExplorerId()));
            territories.save(from);
        });
    }

    /** 공유 지도 자리 정리(지도 하나만 고친다). 결과는 공개 이벤트로 — 방문 숨김·복구·세트 배경은 기존 구독자가 한다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onMembershipHandover(MembershipHandover event) {
        MapId mapId = MapId.of(event.mapId());
        maps.findLocked(mapId).ifPresent(map -> map.handOver(ExplorerId.of(event.fromExplorerId()),
            ExplorerId.of(event.intoExplorerId()), clock.instant(), settings.leaveGrace()).ifPresent(handover -> {
                maps.save(map);
                publish(map, handover, event, clock.instant());
            }));
    }

    private void publish(ExpeditionMap map, Handover handover, MembershipHandover event, Instant at) {
        String mapId = map.id().value();
        handover.purgedDeparture().ifPresent(expired -> outbox.append(MapService.AGGREGATE, mapId,
            new MemberPurged(mapId, expired.explorerId().value(), at)));
        handover.joined().ifPresent(joined -> outbox.append(MapService.AGGREGATE, mapId, new MemberJoined(mapId,
            joined.explorerId().value(), joined.role().name(), at, handover.rejoined())));
        // 탈퇴(MemberLeft)를 내지 않는다 — 기존 구독자가 선점을 다른 멤버에게 넘기기 때문(Q2). 재귀속은 아래 이벤트로.
        outbox.append(MapService.AGGREGATE, mapId, new MemberReassigned(mapId, event.fromExplorerId(), event.intoExplorerId(), at));
    }

    /**
     * 공유 지도 재귀속(그 지도 territory 하나만 고친다): from 방문을 선점 순서 그대로 into 의 것으로. 충돌은 선점 순서가 이른 쪽.
     * into 의 진행·인벤토리(선점 XP refId 가 수령자를 포함 — claim:{map}:{code}:{into})는 재계산 예약으로 맞춘다. 멱등.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onMemberReassigned(MemberReassigned event) {
        MapId mapId = MapId.of(event.mapId());
        if (!territories.lock(mapId)) return;
        Territory territory = territories.load(mapId);
        ReassignResult result = territory.reassignMember(ExplorerId.of(event.fromExplorerId()), ExplorerId.of(event.intoExplorerId()));
        territories.save(territory);
        recalculations.request(event.intoExplorerId(), RECALCULATION_REASON, clock.instant());
        log.debug("공유 지도 재귀속 {}: {} → {} (그대로 {}, 바꿈 {}, 버림 {})", mapId, event.fromExplorerId(), event.intoExplorerId(),
            result.reassigned().size(), result.replaced().size(), result.dropped().size());
    }

    private static List<String> codes(List<RegionCode> regions) {
        return regions.stream().map(RegionCode::value).toList();
    }
}
