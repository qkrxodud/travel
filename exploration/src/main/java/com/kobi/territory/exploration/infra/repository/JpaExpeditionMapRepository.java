package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.ExpeditionMapJpaEntity;
import com.kobi.territory.exploration.infra.entity.MapMemberJpaEntity;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.map.CountryCode;
import com.kobi.territory.exploration.domain.map.ExpeditionMap;
import com.kobi.territory.exploration.domain.map.ExpeditionMapRepository;
import com.kobi.territory.exploration.domain.map.InviteCode;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.map.MapKind;
import com.kobi.territory.exploration.domain.map.MapSettings;
import com.kobi.territory.exploration.domain.map.MapVisibility;
import com.kobi.territory.exploration.domain.map.Member;
import com.kobi.territory.exploration.domain.map.MemberRole;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaExpeditionMapRepository implements ExpeditionMapRepository {

    private final ExpeditionMapJpaRepository maps;
    private final MapMemberJpaRepository members;

    JpaExpeditionMapRepository(ExpeditionMapJpaRepository maps, MapMemberJpaRepository members) {
        this.maps = maps;
        this.members = members;
    }

    @Override
    public void save(ExpeditionMap map) {
        ExpeditionMapJpaEntity mapEntity = maps.findById(map.id().value())
            .orElseGet(() -> ExpeditionMapJpaEntity.from(map));
        mapEntity.apply(map);
        maps.save(mapEntity);
        members.findByMapId(map.id().value()).stream()
            .filter(memberEntity -> map.member(memberEntity.explorerId()).isEmpty())
            .forEach(members::delete);
        map.members().forEach(member -> members.save(MapMemberJpaEntity.from(map.id(), member)));
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
    public boolean existsByInviteCode(InviteCode code) {
        return maps.existsByInviteCode(code.value());
    }

    @Override
    public List<MapId> mapIdsOf(ExplorerId explorerId) {
        return members.findByExplorerId(explorerId.value()).stream().map(MapMemberJpaEntity::mapId).toList();
    }

    private ExpeditionMap toDomain(ExpeditionMapJpaEntity mapEntity) {
        return mapEntity.toDomain(members.findByMapId(mapEntity.id()));
    }
}
