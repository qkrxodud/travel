package com.kobi.territory.analytics.application;

import com.kobi.territory.analytics.domain.actor.ActorKey;
import com.kobi.territory.analytics.domain.actor.Country;
import com.kobi.territory.analytics.domain.actor.DeviceType;
import com.kobi.territory.analytics.domain.actor.ExplorerHash;
import com.kobi.territory.analytics.domain.actor.ExplorerHasher;
import com.kobi.territory.analytics.domain.actor.VisitorId;
import com.kobi.territory.analytics.domain.actor.VisitorLink;
import com.kobi.territory.analytics.domain.actor.VisitorRepository;
import com.kobi.territory.analytics.domain.ratelimit.IngestThrottle;
import com.kobi.territory.analytics.domain.ratelimit.ProxySetupWarning;
import com.kobi.territory.analytics.domain.tracking.BatchOutcome;
import com.kobi.territory.analytics.domain.tracking.ClientContext;
import com.kobi.territory.analytics.domain.tracking.EventBatch;
import com.kobi.territory.analytics.domain.tracking.EventDefinitions;
import com.kobi.territory.analytics.domain.tracking.TrackedEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 화면 이벤트 수집({@code POST /events}). 순서: 레이트 리밋(요청 주소는 믿는 프록시 규칙으로 — TrustedProxies) → 묶음 크기 → (한 트랜잭션) 방문 기록·탐험가 연결 → 이벤트마다 검증해 적기 →
 * 처음 이어진 방문이면 예전 이벤트를 탐험가로 다시 묶기. 탐험가 id 는 해시로만, User-Agent·주소는 판단에만 쓰고 버린다.
 * 트랜잭션은 READ_COMMITTED — 같은 방문의 묶음이 동시에 와도 방문 행의 "비어 있을 때만 채우기"가 남이 커밋한 값을 읽는다.
 */
@Service
public class ClientEventService {

    private static final Logger log = LoggerFactory.getLogger(ClientEventService.class);

    private final VisitorRepository visitors;
    private final TrackedEventRepository events;
    private final AnalyticsSettings settings;
    private final ExplorerHasher hasher;
    private final IngestThrottle throttle;
    private final ProxySetupWarning proxySetupWarning;
    private final EventDefinitions definitions = EventDefinitions.standard();
    private final TransactionTemplate writeTx;
    private final Clock clock;

    public ClientEventService(VisitorRepository visitors, TrackedEventRepository events, AnalyticsSettings settings,
                              PlatformTransactionManager transactionManager, Clock clock) {
        this.visitors = visitors;
        this.events = events;
        this.settings = settings;
        this.hasher = new ExplorerHasher(settings.salt());
        this.throttle = new IngestThrottle(settings.rateLimitPolicy());
        this.proxySetupWarning = new ProxySetupWarning(settings.trustedProxies(), Duration.ofHours(1));
        this.writeTx = new TransactionTemplate(transactionManager);
        this.writeTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.clock = clock;
    }

    public BatchOutcome ingest(ClientEvents command) {
        Instant now = clock.instant();
        VisitorId visitorId = VisitorId.of(command.visitorId());
        if (proxySetupWarning.shouldWarn(command.origin(), now)) {
            log.warn("분석 수집: 프록시 헤더(CF-Connecting-IP·X-Forwarded-For)가 사설 주소 {} 에서 왔는데 믿는 프록시가 아니다 — "
                + "TERRITORY_TRUSTED_PROXIES 가 비었거나 맞지 않으면 모든 방문자가 프록시 주소 하나로 세져 수집이 429 로 버려질 수 있다"
                + "(doc/operations.md 배포 체크리스트). 1시간에 한 번만 알린다", command.origin().socketAddress());
        }
        throttle.admit(visitorId.value(), settings.trustedProxies().clientAddress(command.origin()), now);
        EventBatch batch = EventBatch.of(command.events(), settings.ingestPolicy());
        ExplorerHash explorerHash = Optional.ofNullable(command.explorerId()).map(hasher::hash).orElse(null);
        DeviceType device = DeviceType.classify(command.userAgent());
        Country country = Country.fromHeader(settings.trustedProxies().countryHeader(command.origin())).orElse(null);
        return writeTx.execute(status -> {
            VisitorLink link = batch.sighting(definitions, visitorId, explorerHash, device, now).map(visitors::record).orElse(VisitorLink.NONE);
            ActorKey actor = link.actorFor(visitorId, explorerHash);
            BatchOutcome outcome = batch.accept(definitions, new ClientContext(visitorId, explorerHash, actor, device, country, now));
            events.appendAll(outcome.accepted());
            link.relinkTarget().ifPresent(explorerActor -> events.relinkVisitor(visitorId, explorerActor));
            return outcome;
        });
    }
}
