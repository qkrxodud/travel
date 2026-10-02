package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.ExpeditionMapJpaEntity;
import com.kobi.territory.exploration.infra.entity.MapMemberJpaEntity;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.map.ExpeditionMap;
import com.kobi.territory.exploration.domain.map.ExpeditionMapRepository;
import com.kobi.territory.exploration.domain.map.InviteCode;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.map.MapKind;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * ExpeditionMap 저장소 어댑터 — expedition_map(루트)·map_member(현재 멤버 + 탈퇴 유예 중) 테이블.
 * save 는 지금 행과 비교해 바뀐 행만 반영한다(추가·갱신·삭제).
 */
@Repository
class JpaExpeditionMapRepository implements ExpeditionMapRepository {

    private final ExpeditionMapJpaRepository maps;
    private final MapMemberJpaRepository members;
    private final EntityManager entityManager;

    JpaExpeditionMapRepository(ExpeditionMapJpaRepository maps, MapMemberJpaRepository members, EntityManager entityManager) {
        this.maps = maps;
        this.members = members;
        this.entityManager = entityManager;
    }

    @Override
    public void save(ExpeditionMap map) {
        ExpeditionMapJpaEntity mapEntity = maps.findById(map.id().value())
            .orElseGet(() -> ExpeditionMapJpaEntity.from(map));
        mapEntity.apply(map);
        maps.save(mapEntity);
        Map<String, MapMemberJpaEntity> existing = new HashMap<>();
        members.findByMapId(map.id().value()).forEach(row -> existing.put(row.explorerId().value(), row));
        map.members().forEach(member -> {
            MapMemberJpaEntity row = existing.remove(member.explorerId().value());
            if (row == null) members.save(MapMemberJpaEntity.from(map.id(), member));
            else row.apply(member);
        });
        map.departures().forEach(departure -> {
            MapMemberJpaEntity row = existing.remove(departure.explorerId().value());
            if (row == null) members.save(MapMemberJpaEntity.from(map.id(), departure));
            else row.apply(departure);
        });
        members.deleteAll(existing.values());
        members.flush();
    }

    @Override
    public Optional<ExpeditionMap> findById(MapId id) {
        return maps.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<ExpeditionMap> findPersonalMap(ExplorerId owner) {
        return maps.findFirstByOwnerIdAndKind(owner.value(), MapKind.PERSONAL.name()).map(this::toDomain);
    }

    @Override
    public Optional<ExpeditionMap> findLocked(MapId id) {
        // FOR UPDATE 로 기다려 잡은 뒤(PESSIMISTIC_WRITE) version 을 강제 증가한다 — 처음부터 FORCE_INCREMENT 로 잠그면 MySQL 에서
        // NOWAIT 로 나가 경합이 곧바로 실패한다. 이미 자기가 잡은 행이라 두 번째 잠금은 바로 얻는다.
        return maps.lockById(id.value()).map(mapEntity -> {
            entityManager.lock(mapEntity, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
            return toDomain(mapEntity);
        });
    }

    @Override
    public boolean lockShared(MapId id) {
        return maps.lockSharedById(id.value()).isPresent();
    }

    @Override
    public Optional<MapId> personalMapIdOf(ExplorerId owner) {
        return maps.findPersonalMapIds(owner.value()).stream().findFirst().map(MapId::of);
    }

    @Override
    public Optional<MapId> findIdByInviteCode(InviteCode code) {
        return maps.findIdByInviteCode(code.value()).map(MapId::of);
    }

    @Override
    public boolean isMember(MapId mapId, ExplorerId explorerId) {
        return members.existsByMapIdAndExplorerIdAndLeftAtIsNull(mapId.value(), explorerId.value());
    }

    @Override
    public boolean exists(MapId mapId) {
        return maps.existsById(mapId.value());
    }

    @Override
    public boolean existsByInviteCode(InviteCode code) {
        return maps.existsByInviteCode(code.value());
    }

    @Override
    public List<MapId> mapIdsOf(ExplorerId explorerId) {
        return members.findByExplorerIdAndLeftAtIsNull(explorerId.value()).stream().map(MapMemberJpaEntity::mapId).toList();
    }

    @Override
    public List<ExpeditionMap> mapsOf(ExplorerId explorerId) {
        return mapIdsOf(explorerId).stream().map(this::findById).flatMap(Optional::stream)
            .sorted(Comparator.comparing((ExpeditionMap map) -> map.kind() != MapKind.PERSONAL)
                .thenComparing(ExpeditionMap::createdAt))
            .toList();
    }

    @Override
    public List<MapId> mapIdsWithDeparturesBefore(Instant leftBefore) {
        return members.mapIdsWithDeparturesBefore(leftBefore).stream().map(MapId::of).toList();
    }

    private ExpeditionMap toDomain(ExpeditionMapJpaEntity mapEntity) {
        return mapEntity.toDomain(members.findByMapId(mapEntity.id()));
    }
}
