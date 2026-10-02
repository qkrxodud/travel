package com.kobi.territory.exploration.infra;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.CountryCode;
import com.kobi.territory.exploration.domain.ExpeditionMap;
import com.kobi.territory.exploration.domain.ExpeditionMapRepository;
import com.kobi.territory.exploration.domain.InviteCode;
import com.kobi.territory.exploration.domain.MapId;
import com.kobi.territory.exploration.domain.MapKind;
import com.kobi.territory.exploration.domain.MapSettings;
import com.kobi.territory.exploration.domain.MapVisibility;
import com.kobi.territory.exploration.domain.Member;
import com.kobi.territory.exploration.domain.MemberRole;
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
        MapSettings settings = map.settings();
        maps.save(new ExpeditionMapJpaEntity(map.id().value(), map.name(), map.country().value(), map.inviteCode().value(),
            map.ownerId().value(), map.kind().name(), settings.photoRequired(), settings.dailyCheckInCap(), settings.visibility().name(),
            map.createdAt()));
        var existing = members.findByMapId(map.id().value());
        existing.stream()
            .filter(memberEntity -> map.member(ExplorerId.of(memberEntity.getExplorerId())).isEmpty())
            .forEach(members::delete);
        for (Member member : map.members()) {
            members.save(new MapMemberJpaEntity(map.id().value(), member.explorerId().value(), member.role().name(), member.joinedAt()));
        }
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
        return members.findByExplorerId(explorerId.value()).stream().map(memberEntity -> MapId.of(memberEntity.getMapId())).toList();
    }

    private ExpeditionMap toDomain(ExpeditionMapJpaEntity entity) {
        var memberList = members.findByMapId(entity.getId()).stream()
            .map(memberEntity -> new Member(ExplorerId.of(memberEntity.getExplorerId()), MemberRole.valueOf(memberEntity.getRole()), memberEntity.getJoinedAt()))
            .toList();
        return ExpeditionMap.restore(MapId.of(entity.getId()), entity.getName(), new CountryCode(entity.getCountryCode()),
            new InviteCode(entity.getInviteCode()), ExplorerId.of(entity.getOwnerId()), MapKind.valueOf(entity.getKind()),
            new MapSettings(entity.isPhotoRequired(), entity.getDailyCheckInCap(), MapVisibility.valueOf(entity.getVisibility())),
            entity.getCreatedAt(), memberList);
    }
}
