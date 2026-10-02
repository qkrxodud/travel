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
        MapSettings s = map.settings();
        maps.save(new ExpeditionMapJpaEntity(map.id().value(), map.name(), map.country().value(), map.inviteCode().value(),
            map.ownerId().value(), map.kind().name(), s.photoRequired(), s.dailyCheckInCap(), s.visibility().name(),
            map.createdAt()));
        var existing = members.findByMapId(map.id().value());
        existing.stream()
            .filter(e -> map.member(ExplorerId.of(e.getExplorerId())).isEmpty())
            .forEach(members::delete);
        for (Member m : map.members()) {
            members.save(new MapMemberJpaEntity(map.id().value(), m.explorerId().value(), m.role().name(), m.joinedAt()));
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

    private ExpeditionMap toDomain(ExpeditionMapJpaEntity e) {
        var memberList = members.findByMapId(e.getId()).stream()
            .map(m -> new Member(ExplorerId.of(m.getExplorerId()), MemberRole.valueOf(m.getRole()), m.getJoinedAt()))
            .toList();
        return ExpeditionMap.restore(MapId.of(e.getId()), e.getName(), new CountryCode(e.getCountryCode()),
            new InviteCode(e.getInviteCode()), ExplorerId.of(e.getOwnerId()), MapKind.valueOf(e.getKind()),
            new MapSettings(e.isPhotoRequired(), e.getDailyCheckInCap(), MapVisibility.valueOf(e.getVisibility())),
            e.getCreatedAt(), memberList);
    }
}
