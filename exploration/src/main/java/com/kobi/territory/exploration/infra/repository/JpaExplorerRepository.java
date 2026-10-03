package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.explorer.AccessTokenHash;
import com.kobi.territory.exploration.domain.explorer.Explorer;
import com.kobi.territory.exploration.domain.explorer.ExplorerRepository;
import com.kobi.territory.exploration.domain.explorer.Handle;
import com.kobi.territory.exploration.infra.entity.AccountJpaEntity;
import com.kobi.territory.exploration.infra.entity.ExplorerJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.infra.entity.HandleReservationJpaEntity;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Explorer 저장소 어댑터 — explorer 행 + account 행(1:1, 연결 후 불변이라 없을 때만 insert). */
@Repository
class JpaExplorerRepository implements ExplorerRepository {

    private final ExplorerJpaRepository jpa;
    private final AccountJpaRepository accounts;
    private final HandleReservationJpaRepository reservations;
    private final EntityManager entityManager;

    JpaExplorerRepository(ExplorerJpaRepository jpa, AccountJpaRepository accounts, HandleReservationJpaRepository reservations,
                          EntityManager entityManager) {
        this.jpa = jpa;
        this.accounts = accounts;
        this.reservations = reservations;
        this.entityManager = entityManager;
    }

    @Override
    public void save(Explorer explorer) {
        jpa.findById(explorer.id().value()).ifPresentOrElse(row -> row.apply(explorer),
            () -> jpa.save(ExplorerJpaEntity.from(explorer)));
        explorer.account().filter(account -> !accounts.existsById(explorer.id().value()))
            .ifPresent(account -> accounts.save(AccountJpaEntity.from(explorer.id().value(), account)));
        saveReservations(explorer);
        try {
            jpa.flush(); // handle·계정 UNIQUE 위반을 이 트랜잭션 안에서 드러낸다(호출자가 재시도)
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception, explorer);
        }
    }

    /** 놓은 handle 예약(자식 행): 있는 행은 기한 갱신, 없는 행은 추가, 빠진 행은 삭제. */
    private void saveReservations(Explorer explorer) {
        Map<HandleReservationJpaEntity.Key, HandleReservationJpaEntity> existing = new HashMap<>();
        reservations.findByExplorerId(explorer.id().value()).forEach(row -> existing.put(row.key(), row));
        for (var reservation : explorer.handleReservations()) {
            HandleReservationJpaEntity row = existing.remove(HandleReservationJpaEntity.keyOf(explorer.id().value(), reservation));
            if (row == null) reservations.save(HandleReservationJpaEntity.from(explorer.id().value(), reservation));
            else row.apply(reservation);
        }
        reservations.deleteAll(existing.values());
    }

    /** explorer.handle UNIQUE 위반(동시에 같은 handle) → HANDLE_TAKEN(QA P3-13). 그 밖(계정 UNIQUE 등)은 그대로 — 로그인이 재시도한다. */
    static RuntimeException translate(DataIntegrityViolationException exception, Explorer explorer) {
        String cause = String.valueOf(exception.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        return cause.contains(HANDLE_UNIQUE) && explorer.handle() != null
            ? ExplorationError.HANDLE_TAKEN.exception(explorer.handle().value()) : exception;
    }

    /** V1 explorer 테이블 handle UNIQUE 제약 이름. */
    static final String HANDLE_UNIQUE = "uq_explorer_handle";

    @Override
    public List<ExplorerId> activeIds() {
        return jpa.findActiveIds().stream().map(ExplorerId::of).toList();
    }

    @Override
    public Optional<Explorer> findById(ExplorerId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<Explorer> findLocked(ExplorerId id) {
        return lock(id, LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public Optional<Explorer> findLockedShared(ExplorerId id) {
        return lock(id, LockModeType.PESSIMISTIC_READ);
    }

    /** 잠금 조회 + refresh — 영속성 컨텍스트에 먼저 올라온 사본이 있어도 잠근 뒤의 최신 행으로 덮는다. */
    private Optional<Explorer> lock(ExplorerId id, LockModeType mode) {
        ExplorerJpaEntity row = entityManager.find(ExplorerJpaEntity.class, id.value());
        if (row == null) return Optional.empty();
        entityManager.refresh(row, mode);
        return Optional.of(toDomain(row));
    }

    @Override
    public Optional<Explorer> findByAccount(String provider, String subject) {
        return accounts.findByProviderAndSubject(provider, subject)
            .flatMap(account -> jpa.findById(account.explorerId()).map(row -> toDomain(row, account)));
    }

    @Override
    public Optional<ExplorerId> findIdByTokenHash(AccessTokenHash tokenHash) {
        return jpa.findByAccessTokenHash(tokenHash.value()).map(ExplorerJpaEntity::explorerId);
    }

    @Override
    public Optional<Explorer> findByHandle(Handle handle) {
        return jpa.findByHandle(handle.value()).map(this::toDomain);
    }

    @Override
    public boolean handleTaken(Handle handle, ExplorerId requester, Instant now) {
        return jpa.existsByHandleAndIdNot(handle.value(), requester.value())
            || reservations.reservedByOther(handle.value(), requester.value(), now);
    }

    private Explorer toDomain(ExplorerJpaEntity row) {
        return toDomain(row, accounts.findById(row.explorerId().value()).orElse(null));
    }

    private Explorer toDomain(ExplorerJpaEntity row, AccountJpaEntity account) {
        return row.toDomain(account, reservations.findByExplorerId(row.explorerId().value()));
    }
}
