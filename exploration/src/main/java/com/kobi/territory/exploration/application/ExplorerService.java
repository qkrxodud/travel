package com.kobi.territory.exploration.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.event.MapCreated;
import com.kobi.territory.exploration.domain.CountryCode;
import com.kobi.territory.exploration.domain.ExpeditionMap;
import com.kobi.territory.exploration.domain.ExpeditionMapRepository;
import com.kobi.territory.exploration.domain.Explorer;
import com.kobi.territory.exploration.domain.ExplorerRepository;
import com.kobi.territory.exploration.domain.InviteCode;
import com.kobi.territory.exploration.domain.MapId;
import com.kobi.territory.exploration.domain.MapKind;
import com.kobi.territory.exploration.domain.MapSettings;
import com.kobi.territory.exploration.domain.TerritoryRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탐험가 발급. 1~3단계는 익명 탐험가 + 개인 지도 자동 생성(멤버 1명 OWNER, MapSettings 기본값).
 * 생성 시 예외(domain-model §2-9): Explorer와 개인 ExpeditionMap(+territory 행)은 둘 다 신규 행이라 잠금 경합이 없고
 * 원자성이 필요하므로(개인 지도 없는 탐험가 방지) 한 트랜잭션에서 함께 만든다.
 */
@Service
public class ExplorerService {

    public static final String PERSONAL_MAP_NAME = "나의 영토";

    private final ExplorerRepository explorers;
    private final ExpeditionMapRepository maps;
    private final TerritoryRepository territories;
    private final EventOutbox outbox;
    private final ExplorationSettings settings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ExplorerService(ExplorerRepository explorers, ExpeditionMapRepository maps, TerritoryRepository territories,
                           EventOutbox outbox, ExplorationSettings settings, Clock clock) {
        this.explorers = explorers;
        this.maps = maps;
        this.territories = territories;
        this.outbox = outbox;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public RegisteredExplorer registerAnonymous() {
        Instant now = clock.instant();
        Explorer explorer = Explorer.anonymous(ExplorerId.newId(), now);
        explorers.save(explorer);
        ExpeditionMap map = ExpeditionMap.create(MapId.newId(), explorer.id(), PERSONAL_MAP_NAME, CountryCode.KR,
            newInviteCode(), MapKind.PERSONAL, MapSettings.defaults(settings.defaultDailyCap()), now);
        maps.save(map);
        territories.create(map.id(), now); // 빈 Territory 루트 행(체크인 직렬화 잠금 대상)
        outbox.append("ExpeditionMap", map.id().value(),
            new MapCreated(map.id().value(), explorer.id().value(), map.kind().name(), map.country().value(), now));
        return new RegisteredExplorer(explorer, map);
    }

    private InviteCode newInviteCode() {
        for (int i = 0; i < 10; i++) {
            InviteCode code = InviteCode.generate(random);
            if (!maps.existsByInviteCode(code)) return code;
        }
        throw new IllegalStateException("초대코드 생성 실패(충돌 반복)");
    }

    public record RegisteredExplorer(Explorer explorer, ExpeditionMap personalMap) {}
}
