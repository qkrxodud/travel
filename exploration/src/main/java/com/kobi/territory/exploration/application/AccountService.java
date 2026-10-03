package com.kobi.territory.exploration.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.event.ExplorerMerged;
import com.kobi.territory.exploration.api.event.HandleChanged;
import com.kobi.territory.exploration.api.query.AccountCredentials;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.ExplorationException;
import com.kobi.territory.exploration.domain.explorer.Account;
import com.kobi.territory.exploration.domain.explorer.AccountIdentity;
import com.kobi.territory.exploration.domain.explorer.Explorer;
import com.kobi.territory.exploration.domain.explorer.ExplorerRepository;
import com.kobi.territory.exploration.domain.explorer.Handle;
import com.kobi.territory.exploration.domain.explorer.LoginPlan;
import com.kobi.territory.exploration.domain.map.ExpeditionMapRepository;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.territory.MergeSummary;
import com.kobi.territory.exploration.domain.territory.TerritoryRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 계정 로그인·handle (4단계). 무엇을 할지(로그인만·연결·새로 만들기·병합)는 도메인 {@link LoginPlan} 이 정하고, 여기는 잠금·저장·outbox 만 한다.
 * <p>
 * 병합(claimExplorer, 사용자 확정 "기존 계정으로 병합")은 여러 애그리거트에 걸치므로 이 트랜잭션은 익명 탐험가 A(Explorer) 하나만 고친다
 * (MERGED·토큰 무효) → outbox: {@link ExplorerMerged}(→ 개인 지도 방문 이동, {@link ExplorerMergeService}) + A 가 멤버인 공유 지도마다
 * {@link MembershipHandover}(→ 지도별 자리 정리). 같은 병합을 다시 해도 A 가 이미 MERGED 라 로그인만 된다(멱등).
 * <p>
 * 동시성: 트랜잭션은 A 의 탐험가 행 배타 잠금으로 시작한다(READ_COMMITTED). 체크인·지도 커맨드는 지도/territory 잠금 뒤 탐험가 행을
 * 공유 잠금으로 다시 확인하므로(MapAccess.requireActiveLocked) — 병합보다 먼저 잠근 쓰기는 병합이 그 커밋을 기다렸다 읽고(옮겨짐),
 * 병합 뒤의 쓰기는 거절된다. 같은 계정으로 두 기기가 동시에 처음 로그인하면 account UNIQUE(provider, subject) 에 한쪽이 걸리고, 다시
 * 시도하면 이미 생긴 계정 탐험가로 병합된다. handle 자동 발급 충돌(UNIQUE)도 다시 시도로 다음 접미사를 받는다.
 */
@Service
public class AccountService implements AccountCredentials {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final int MAX_ATTEMPTS = 3;
    static final String EXPLORER_AGGREGATE = "Explorer";

    private final ExplorerRepository explorers;
    private final ExpeditionMapRepository maps;
    private final TerritoryRepository territories;
    private final ExplorerService explorerService;
    private final EventOutbox outbox;
    private final Clock clock;
    private final AccountSettings settings;
    private final TransactionTemplate loginTx;
    private final SecureRandom random = new SecureRandom();

    public AccountService(ExplorerRepository explorers, ExpeditionMapRepository maps, TerritoryRepository territories,
                          ExplorerService explorerService, EventOutbox outbox, Clock clock, AccountSettings settings,
                          PlatformTransactionManager transactionManager) {
        this.settings = settings;
        this.explorers = explorers;
        this.maps = maps;
        this.territories = territories;
        this.explorerService = explorerService;
        this.outbox = outbox;
        this.clock = clock;
        this.loginTx = new TransactionTemplate(transactionManager);
        this.loginTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * 로그인(구글 OIDC 성공 또는 local /dev/login). 세션은 호출자(app-api)가 만든다.
     * @param currentExplorerId 지금 기기의 익명 탐험가(토큰으로 확인된 것). 없으면 null
     */
    public LoginOutcome login(AccountIdentity identity, ExplorerId currentExplorerId) {
        for (int attempt = 1; ; attempt++) {
            try {
                return loginTx.execute(status -> loginLocked(identity, currentExplorerId));
            } catch (DataIntegrityViolationException | ConcurrencyFailureException exception) {
                if (attempt >= MAX_ATTEMPTS) throw exception;
                log.info("로그인 동시성 충돌 {}회째 — 다시 시도: {}", attempt, exception.toString());
            } catch (ExplorationException exception) {
                // 자동 발급한 랜덤 handle 이 동시에 겹침(UNIQUE → HANDLE_TAKEN) — 다시 뽑는다
                if (exception.error() != ExplorationError.HANDLE_TAKEN || attempt >= MAX_ATTEMPTS) throw exception;
                log.info("로그인 handle 충돌 {}회째 — 다시 시도", attempt);
            }
        }
    }

    private LoginOutcome loginLocked(AccountIdentity identity, ExplorerId currentExplorerId) {
        Optional<Explorer> current = Optional.ofNullable(currentExplorerId).flatMap(explorers::findLocked); // 첫 문장: A 배타 잠금
        LoginPlan plan = LoginPlan.decide(explorers.findByAccount(identity.provider(), identity.subject()), current);
        Instant now = clock.instant();
        return switch (plan.kind()) {
            case SIGN_IN -> LoginOutcome.of(LoginPlan.Kind.SIGN_IN, plan.accountExplorer(), personalMapOf(plan.accountExplorer()));
            case LINK -> link(LoginPlan.Kind.LINK, plan.current(), identity, now);
            case CREATE -> link(LoginPlan.Kind.CREATE, explorerService.create(now).explorer(), identity, now);
            case MERGE -> merge(plan.current(), plan.accountExplorer(), now);
        };
    }

    private LoginOutcome link(LoginPlan.Kind kind, Explorer explorer, AccountIdentity identity, Instant now) {
        Explorer.HandleChange change = explorer.linkAccount(new Account(identity, now),
            Handle.random(random, handle -> explorers.handleTaken(handle, explorer.id(), now)));
        explorers.save(explorer);
        outbox.append(EXPLORER_AGGREGATE, explorer.id().value(), handleChanged(change));
        return LoginOutcome.of(kind, explorer, personalMapOf(explorer));
    }

    private LoginOutcome merge(Explorer anonymous, Explorer target, Instant now) {
        MapId fromMap = personalMapOf(anonymous);
        MapId intoMap = personalMapOf(target);
        MergeSummary summary = territories.load(intoMap).mergeSummary(territories.load(fromMap), anonymous.id(), target.id());
        anonymous.mergeInto(target, now).orElseThrow();
        explorers.save(anonymous);
        outbox.append(EXPLORER_AGGREGATE, target.id().value(), new ExplorerMerged(anonymous.id().value(), target.id().value(),
            fromMap.value(), intoMap.value(), now));
        maps.sharedMapIdsOf(anonymous.id()).forEach(mapId -> outbox.append(MapService.AGGREGATE, mapId.value(),
            new MembershipHandover(mapId.value(), anonymous.id().value(), target.id().value(), now)));
        return new LoginOutcome(LoginPlan.Kind.MERGE, target, intoMap, anonymous.id(), summary);
    }

    /**
     * handle 변경(계정 탐험가만). 유일성(다른 탐험가의 사용·예약)은 도메인이 저장소 확인으로 판단하고, 동시 변경은 DB UNIQUE 가 막는다
     * (어댑터가 HANDLE_TAKEN 409 로 번역). 옛 handle 은 territory.account.handle-reservation-days 동안 예약된다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Explorer changeHandle(ExplorerId explorerId, String requested) {
        Handle handle = Handle.of(requested);
        Explorer explorer = explorers.findLocked(explorerId).filter(Explorer::active)
            .orElseThrow(ExplorationError.EXPLORER_NOT_FOUND::exception);
        Instant now = clock.instant();
        explorer.changeHandle(handle, now, settings.handleReservation(),
            candidate -> explorers.handleTaken(candidate, explorerId, now)).ifPresent(change -> {
            explorers.save(explorer);
            outbox.append(EXPLORER_AGGREGATE, explorer.id().value(), handleChanged(change));
        });
        return explorer;
    }

    /** 로그인 화면·세션 조회용. 없거나 비활성이면 빈 값. */
    @Transactional(readOnly = true)
    public Optional<AccountView> view(ExplorerId explorerId) {
        return explorers.findById(explorerId).filter(Explorer::active)
            .map(explorer -> new AccountView(explorer, personalMapOf(explorer)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> explorerIdByAccount(String provider, String subject) {
        return explorers.findByAccount(provider, subject).filter(Explorer::active).map(explorer -> explorer.id().value());
    }

    private MapId personalMapOf(Explorer explorer) {
        return maps.personalMapIdOf(explorer.id()).orElseThrow(ExplorationError.EXPLORER_NOT_FOUND::exception);
    }

    private static HandleChanged handleChanged(Explorer.HandleChange change) {
        return new HandleChanged(change.explorerId().value(), change.previous() == null ? null : change.previous().value(),
            change.current().value(), change.at());
    }

    /**
     * @param explorer     로그인 세션이 가리킬 탐험가(병합이면 계정 탐험가 B)
     * @param mergedFrom   병합된 익명 탐험가(MERGE 일 때만)
     * @param mergeSummary 병합 안내(MERGE 일 때만) — 방문 이동은 비동기라 로그인 시점에 미리 계산한 값
     */
    public record LoginOutcome(LoginPlan.Kind kind, Explorer explorer, MapId personalMapId, ExplorerId mergedFrom,
                               MergeSummary mergeSummary) {
        static LoginOutcome of(LoginPlan.Kind kind, Explorer explorer, MapId personalMapId) {
            return new LoginOutcome(kind, explorer, personalMapId, null, null);
        }
    }

    public record AccountView(Explorer explorer, MapId personalMapId) {}
}
