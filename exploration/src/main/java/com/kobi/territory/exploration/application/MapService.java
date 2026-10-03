package com.kobi.territory.exploration.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.event.MapCreated;
import com.kobi.territory.exploration.api.event.MapSettingsChanged;
import com.kobi.territory.exploration.api.event.MemberJoined;
import com.kobi.territory.exploration.api.event.MemberLeft;
import com.kobi.territory.exploration.api.event.MemberPurged;
import com.kobi.territory.exploration.api.query.ExplorerProfileQuery;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.map.CountryCode;
import com.kobi.territory.exploration.domain.map.Departure;
import com.kobi.territory.exploration.domain.map.ExpeditionMap;
import com.kobi.territory.exploration.domain.map.ExpeditionMapRepository;
import com.kobi.territory.exploration.domain.map.InviteCode;
import com.kobi.territory.exploration.domain.map.JoinResult;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.map.MapKind;
import com.kobi.territory.exploration.domain.map.MapSelector;
import com.kobi.territory.exploration.domain.map.MapSettings;
import com.kobi.territory.exploration.domain.territory.Territory;
import com.kobi.territory.exploration.domain.territory.TerritoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공유 지도(ExpeditionMap) 유스케이스 — "불러와서 → 도메인에 시키고 → 저장 → outbox". 멤버·권한·유예 판단은 ExpeditionMap 이 한다.
 * 지도 커맨드는 Territory 를 같은 트랜잭션에서 고치지 않는다(§2-9): 탈퇴·재가입·유예 종료의 방문 숨김·복구·삭제는
 * MemberLeft·MemberJoined·MemberPurged 를 구독하는 {@link TerritoryMembershipService} 가 territory 잠금으로 처리한다.
 * 예외 — 지도 생성은 ExpeditionMap + 빈 territory 행을 함께 만든다(둘 다 신규 행, 생성 시 예외 §2-9).
 * <p>
 * 동시성(QA P1-1): 지도 커맨드는 expedition_map 행을 배타 잠금(FOR UPDATE + version 강제 증가)한 뒤 멤버를 읽는다 — 동시 합류로
 * 멤버가 4명을 넘거나, 양도·탈퇴가 엇갈려 OWNER 가 0명이 되거나, 유예 종료와 재가입이 엇갈려 멤버 행이 사라지지 않게.
 * READ_COMMITTED — 잠금 뒤 읽기는 최신 커밋을 본다. 잠금 순서는 지도 행 하나뿐이다(territory 는 구독자가 별도 트랜잭션에서
 * 잠근다). 체크인 경로는 territory 행만 잠그고 지도는 읽기만 하므로 두 경로 사이에 교착이 없다.
 * 멤버가 아닌 요청은 잠그기 전에 거른다(일반 읽기 — N2 와 같은 이유).
 */
@Service
public class MapService {

    static final String AGGREGATE = ExplorerService.MAP_AGGREGATE;

    private final ExpeditionMapRepository maps;
    private final TerritoryRepository territories;
    private final MapAccess mapAccess;
    private final InviteCodes inviteCodes;
    private final EventOutbox outbox;
    private final ExplorationSettings settings;
    private final ExplorerProfileQuery profiles;
    private final ProfileJoinGate profileGate;
    private final Clock clock;

    public MapService(ExpeditionMapRepository maps, TerritoryRepository territories, MapAccess mapAccess,
                      InviteCodes inviteCodes, EventOutbox outbox, ExplorationSettings settings, ExplorerProfileQuery profiles,
                      ProfileJoinGate profileGate, Clock clock) {
        this.profiles = profiles;
        this.profileGate = profileGate;
        this.maps = maps;
        this.territories = territories;
        this.mapAccess = mapAccess;
        this.inviteCodes = inviteCodes;
        this.outbox = outbox;
        this.settings = settings;
        this.clock = clock;
    }

    /** 공유 지도 만들기 — 만든 사람이 지도장. 설정은 기본값(하루 상한 = territory.check-in.daily-cap). */
    @Transactional
    public ExpeditionMap create(ExplorerId owner, String name, String countryCode) {
        mapAccess.requireActiveLocked(owner); // 병합(로그인)과 직렬화 — 병합이 읽는 "공유 지도 목록"에서 새 지도가 빠지지 않게

        Instant now = clock.instant();
        ExpeditionMap map = ExpeditionMap.create(MapId.newId(), owner, name, CountryCode.orKorea(countryCode), inviteCodes.issue(),
            MapKind.SHARED, MapSettings.defaults(settings.defaultDailyCap()), now);
        maps.save(map);
        territories.create(map.id(), now);
        outbox.append(AGGREGATE, map.id().value(),
            new MapCreated(map.id().value(), owner.value(), map.kind().name(), map.country().value(), now));
        outbox.append(AGGREGATE, map.id().value(), new MemberJoined(map.id().value(), owner.value(), "OWNER", now, false));
        return map;
    }

    /** 초대코드로 합류(유예 안이면 재가입 — 방문 복구는 MemberJoined(rejoined) 구독자가). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Joined join(ExplorerId explorerId, String inviteCode) {
        mapAccess.requireExplorer(explorerId);
        InviteCode code = InviteCode.parse(inviteCode)
            .orElseThrow(() -> ExplorationError.INVITE_CODE_NOT_FOUND.exception(String.valueOf(inviteCode)));
        MapId mapId = maps.findIdByInviteCode(code)
            .orElseThrow(() -> ExplorationError.INVITE_CODE_NOT_FOUND.exception(code.value()));
        ExpeditionMap map = lock(mapId);
        mapAccess.requireActiveLocked(explorerId); // 지도 X → 탐험가 S(병합과 직렬화, 4단계)
        Instant now = clock.instant();
        JoinResult result = map.join(explorerId, now, settings.leaveGrace());
        maps.save(map);
        publishJoined(map, explorerId, result, now);
        return new Joined(map, result.rejoined());
    }

    /**
     * 공개 프로필 링크로 합류(4단계, 초대코드 노출 없음). 프로필이 공개(ProfileJoinGate)이고 그 주인이 지도장인 PUBLIC 공유 지도만 — handle 을 모르거나 프로필이 비공개거나
     * 지도가 열려 있지 않으면 PROFILE_MAP_NOT_FOUND(어느 쪽인지 숨긴다). 초대 보상의 초대자 = 프로필 주인(MemberJoined.invitedBy).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Joined joinViaProfile(ExplorerId explorerId, String handle, MapId mapId) {
        mapAccess.requireExplorer(explorerId);
        ExplorerId profileOwner = profiles.explorerIdByHandle(handle).map(ExplorerId::of)
            .orElseThrow(ExplorationError.PROFILE_MAP_NOT_FOUND::exception);
        if (maps.findById(mapId).filter(candidate -> candidate.openToProfileOf(profileOwner)).isEmpty()) {
            throw ExplorationError.PROFILE_MAP_NOT_FOUND.exception();
        }
        ExpeditionMap map = lock(mapId);
        mapAccess.requireActiveLocked(explorerId); // 지도 X → 탐험가 S(병합과 직렬화, 4단계)
        Instant now = clock.instant();
        JoinResult result = map.joinViaProfile(profileOwner, profileGate.profileOpenTo(profileOwner, explorerId), explorerId, now,
            settings.leaveGrace());
        maps.save(map);
        publishJoined(map, explorerId, result, now);
        return new Joined(map, result.rejoined());
    }

    private void publishJoined(ExpeditionMap map, ExplorerId explorerId, JoinResult result, Instant now) {
        result.purgedDeparture().ifPresent(expired -> outbox.append(AGGREGATE, map.id().value(),
            new MemberPurged(map.id().value(), expired.explorerId().value(), now)));
        outbox.append(AGGREGATE, map.id().value(), new MemberJoined(map.id().value(), explorerId.value(),
            result.member().role().name(), now, result.rejoined(), result.invitedBy().value()));
    }

    /** 탈퇴(유예 시작). 방문 숨김·선점 이전은 MemberLeft 구독자가. @return 유예 정보 + 숨겨질 내 영토 수 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Left leave(ExplorerId explorerId, MapId mapId) {
        ExpeditionMap map = lockAsMember(explorerId, mapId);
        int myRegions = territories.load(mapId).visitsOf(explorerId).size();
        Departure departure = map.leave(explorerId, clock.instant());
        maps.save(map);
        outbox.append(AGGREGATE, mapId.value(), new MemberLeft(mapId.value(), explorerId.value(), departure.leftAt(),
            departure.purgeAfter(settings.leaveGrace())));
        return new Left(mapId, myRegions, departure.purgeAfter(settings.leaveGrace()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExpeditionMap transferOwner(ExplorerId requester, MapId mapId, ExplorerId newOwner) {
        ExpeditionMap map = lockAsMember(requester, mapId);
        map.transferOwner(requester, newOwner);
        maps.save(map);
        return map;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExpeditionMap regenerateInviteCode(ExplorerId requester, MapId mapId) {
        ExpeditionMap map = lockAsMember(requester, mapId);
        map.regenerateInviteCode(requester, inviteCodes.issue());
        maps.save(map);
        return map;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExpeditionMap changeSettings(ExplorerId requester, MapId mapId, MapSettings next) {
        ExpeditionMap map = lockAsMember(requester, mapId);
        map.changeSettings(requester, next, settings.defaultDailyCap());
        maps.save(map);
        outbox.append(AGGREGATE, mapId.value(), new MapSettingsChanged(mapId.value(), next.photoRequired(),
            next.dailyCheckInCap(), next.visibility().name(), clock.instant()));
        return map;
    }

    /**
     * 유예가 끝난 탈퇴 기록을 지운다(배치 — {@link MapPurgeJob}). 지도마다 트랜잭션 하나. 숨긴 방문 삭제는 MemberPurged 구독자가.
     * @return 지운 기록 수
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int purgeExpired(MapId mapId) {
        ExpeditionMap map = lock(mapId);
        Instant now = clock.instant();
        List<Departure> expired = map.purgeExpired(now, settings.leaveGrace());
        maps.save(map);
        expired.forEach(departure -> outbox.append(AGGREGATE, mapId.value(),
            new MemberPurged(mapId.value(), departure.explorerId().value(), now)));
        return expired.size();
    }

    /** 유예가 끝난 탈퇴 기록이 있는 지도. */
    @Transactional(readOnly = true)
    public List<MapId> mapsWithExpiredDepartures() {
        return maps.mapIdsWithDeparturesBefore(clock.instant().minus(settings.leaveGrace()));
    }

    /** GET /maps — 내가 멤버인 지도(개인 지도 먼저). */
    @Transactional(readOnly = true)
    public List<ExpeditionMap> mapsOf(ExplorerId explorerId) {
        mapAccess.requireExplorer(explorerId);
        return maps.mapsOf(explorerId);
    }

    /** GET /maps/{id} — 멤버만. 지도 + 그 영토(멤버별 영토 수·선점·이의 표시용). */
    @Transactional(readOnly = true)
    public MapView view(ExplorerId explorerId, MapId mapId) {
        ExpeditionMap map = mapAccess.resolve(explorerId, MapSelector.of(mapId)).map();
        return new MapView(map, territories.load(mapId));
    }

    /**
     * 멤버인지 먼저 확인(잠금 없이, 애그리거트를 불러오지 않고 — 비멤버가 지도 행을 잠그지 못하게)한 뒤 지도 행을 잠그고 불러온다.
     * 잠그기 전에 지도 엔티티를 읽으면 영속성 컨텍스트의 오래된 사본이 잠금 조회 결과를 대신해 version 충돌·오래된 멤버 판단이 난다.
     */
    private ExpeditionMap lockAsMember(ExplorerId explorerId, MapId mapId) {
        mapAccess.requireMembership(explorerId, mapId);
        ExpeditionMap map = lock(mapId);
        mapAccess.requireActiveLocked(explorerId); // 지도 X → 탐험가 S(병합과 직렬화, 4단계)
        return map;
    }

    private ExpeditionMap lock(MapId mapId) {
        return maps.findLocked(mapId).orElseThrow(() -> ExplorationError.MAP_NOT_FOUND.exception(mapId.value()));
    }

    public record Joined(ExpeditionMap map, boolean rejoined) {}

    /** @param hiddenRegions 지도에서 사라질 내 영토 수, @param restoreUntil 이때까지 다시 합류하면 복구 */
    public record Left(MapId mapId, int hiddenRegions, Instant restoreUntil) {}

    public record MapView(ExpeditionMap map, Territory territory) {}
}
