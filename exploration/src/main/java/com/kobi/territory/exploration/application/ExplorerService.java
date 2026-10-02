package com.kobi.territory.exploration.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.event.MapCreated;
import com.kobi.territory.exploration.api.query.ExplorerCredentials;
import com.kobi.territory.exploration.domain.explorer.AccessToken;
import com.kobi.territory.exploration.domain.map.CountryCode;
import com.kobi.territory.exploration.domain.map.ExpeditionMap;
import com.kobi.territory.exploration.domain.map.ExpeditionMapRepository;
import com.kobi.territory.exploration.domain.explorer.Explorer;
import com.kobi.territory.exploration.domain.explorer.ExplorerRepository;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.map.MapKind;
import com.kobi.territory.exploration.domain.map.MapSettings;
import com.kobi.territory.exploration.domain.territory.TerritoryRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탐험가 발급·인증. 1~3단계는 익명 탐험가 + 개인 지도 자동 생성(멤버 1명 OWNER, MapSettings 기본값).
 * 발급 때 비밀 접근 토큰을 만들어 응답에 한 번만 싣고 해시만 저장한다(결정 2).
 * 생성 시 예외(domain-model §2-9): Explorer와 개인 ExpeditionMap(+territory 행)은 둘 다 신규 행이라 잠금 경합이 없고
 * 원자성이 필요하므로(개인 지도 없는 탐험가 방지) 한 트랜잭션에서 함께 만든다.
 */
@Service
public class ExplorerService implements ExplorerCredentials {

    public static final String PERSONAL_MAP_NAME = "나의 영토";
    static final String MAP_AGGREGATE = "ExpeditionMap";

    private final ExplorerRepository explorers;
    private final ExpeditionMapRepository maps;
    private final TerritoryRepository territories;
    private final InviteCodes inviteCodes;
    private final EventOutbox outbox;
    private final ExplorationSettings settings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ExplorerService(ExplorerRepository explorers, ExpeditionMapRepository maps, TerritoryRepository territories,
                           InviteCodes inviteCodes, EventOutbox outbox, ExplorationSettings settings, Clock clock) {
        this.explorers = explorers;
        this.maps = maps;
        this.territories = territories;
        this.inviteCodes = inviteCodes;
        this.outbox = outbox;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public RegisteredExplorer registerAnonymous() {
        Instant now = clock.instant();
        AccessToken token = AccessToken.generate(random);
        Explorer explorer = Explorer.anonymous(ExplorerId.newId(), token.hash(), now);
        explorers.save(explorer);
        ExpeditionMap map = ExpeditionMap.create(MapId.newId(), explorer.id(), PERSONAL_MAP_NAME, CountryCode.KR,
            inviteCodes.issue(), MapKind.PERSONAL, MapSettings.defaults(settings.defaultDailyCap()), now);
        maps.save(map);
        territories.create(map.id(), now); // 빈 Territory 루트 행(체크인 직렬화 잠금 대상)
        outbox.append(MAP_AGGREGATE, map.id().value(),
            new MapCreated(map.id().value(), explorer.id().value(), map.kind().name(), map.country().value(), now));
        return new RegisteredExplorer(explorer, map, token);
    }

    /** 토큰 → explorerId(인증). 형식이 틀린 토큰도 빈 값. */
    @Override
    @Transactional(readOnly = true)
    public Optional<String> explorerIdByToken(String accessToken) {
        return AccessToken.parse(accessToken).flatMap(token -> explorers.findIdByTokenHash(token.hash())).map(ExplorerId::value);
    }

    /** @param accessToken 비밀 접근 토큰 — 이 응답에만 실린다 */
    public record RegisteredExplorer(Explorer explorer, ExpeditionMap personalMap, AccessToken accessToken) {}
}
