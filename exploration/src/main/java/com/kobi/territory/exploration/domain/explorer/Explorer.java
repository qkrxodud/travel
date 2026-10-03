package com.kobi.territory.exploration.domain.explorer;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.ExplorationError;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 탐험가(계정 루트). 처음엔 익명(계정·handle 없음, 비밀 접근 토큰 해시만 — 3단계 결정 2)으로 발급하고, 4단계부터 구글 로그인으로
 * 계정({@link Account})을 연결한다.
 *
 * 불변식
 * - 계정은 탐험가당 하나(연결 후 바꾸지 않음). 계정이 있으면 handle 도 있다.
 * - 계정을 연결하면 익명 접근 토큰은 무효(로그인 세션으로만 접근 — 토큰을 가진 기기가 로그아웃 뒤에도 계정 기록을 보지 않게).
 * - 병합(claimExplorer, 사용자 확정 "기존 계정으로 병합")은 활성 익명 탐험가만, 다른 활성 계정 탐험가에게로만. 병합되면 MERGED
 *   (비활성·토큰 무효)이고 되돌리지 않는다. 같은 대상으로 다시 병합하면 아무것도 하지 않는다(멱등).
 * - handle 변경은 계정 탐험가만.
 */
public final class Explorer {

    private final ExplorerId id;
    private final Instant createdAt;
    private Handle handle;
    private AccessTokenHash tokenHash;
    private Account account;
    private ExplorerStatus status;
    private ExplorerId mergedInto;
    private Instant mergedAt;
    private final HandleReservations reservations;

    private Explorer(ExplorerId id, Handle handle, AccessTokenHash tokenHash, Instant createdAt, Account account,
                     ExplorerStatus status, ExplorerId mergedInto, Instant mergedAt, HandleReservations reservations) {
        this.id = Objects.requireNonNull(id, "id");
        this.handle = handle;
        this.tokenHash = tokenHash;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.account = account;
        this.status = Objects.requireNonNull(status, "status");
        this.mergedInto = mergedInto;
        this.mergedAt = mergedAt;
        this.reservations = reservations == null ? HandleReservations.of(List.of()) : reservations;
        if (account != null && handle == null) throw new IllegalStateException("계정 탐험가는 handle 이 있어야 합니다: " + id);
        if ((status == ExplorerStatus.MERGED) != (mergedInto != null)) throw new IllegalStateException("병합 상태 불일치: " + id);
    }

    /** 익명 탐험가 발급 — 토큰은 호출자가 만들어 해시만 넘긴다. */
    public static Explorer anonymous(ExplorerId id, AccessTokenHash tokenHash, Instant now) {
        return new Explorer(id, null, Objects.requireNonNull(tokenHash, "tokenHash"), now, null, ExplorerStatus.ACTIVE, null, null,
            null);
    }

    /** 1~3단계 행 복원(계정 없음, 활성). @param tokenHash 3단계 이전에 만든 탐험가는 null */
    public static Explorer restore(ExplorerId id, Handle handle, AccessTokenHash tokenHash, Instant createdAt) {
        return new Explorer(id, handle, tokenHash, createdAt, null, ExplorerStatus.ACTIVE, null, null, null);
    }

    public static Explorer restore(ExplorerId id, Handle handle, AccessTokenHash tokenHash, Instant createdAt, Account account,
                                   ExplorerStatus status, ExplorerId mergedInto, Instant mergedAt,
                                   List<HandleReservation> reservations) {
        return new Explorer(id, handle, tokenHash, createdAt, account, status, mergedInto, mergedAt,
            HandleReservations.of(reservations));
    }

    // ---- 커맨드 ----------------------------------------------------------------------------------------------

    /**
     * 계정 연결(최초 로그인). 익명 토큰은 무효가 된다. @param assigned 자동 발급한 handle
     * @return handle 변화(이전 handle 은 익명이라 보통 null)
     */
    public HandleChange linkAccount(Account linked, Handle assigned) {
        requireActive();
        if (account != null) throw ExplorationError.MERGE_NOT_ALLOWED.exception("이미 계정이 연결됨");
        Handle previous = handle;
        this.account = Objects.requireNonNull(linked, "account");
        this.handle = Objects.requireNonNull(assigned, "handle");
        this.tokenHash = null;
        return new HandleChange(id, previous, assigned, linked.linkedAt());
    }

    /**
     * 이 익명 탐험가를 계정 탐험가 target 으로 병합한다. 비활성(MERGED)·토큰 무효가 된다. 같은 target 으로 이미 병합됐으면 빈 값(멱등).
     * @return 새로 병합했으면 그 사실
     */
    public Optional<Merge> mergeInto(Explorer target, Instant at) {
        Objects.requireNonNull(target, "target");
        if (status == ExplorerStatus.MERGED && target.id.equals(mergedInto)) return Optional.empty();
        if (!canMergeInto(target)) throw ExplorationError.MERGE_NOT_ALLOWED.exception(id.value());
        this.status = ExplorerStatus.MERGED;
        this.mergedInto = target.id;
        this.mergedAt = Objects.requireNonNull(at, "at");
        this.tokenHash = null;
        return Optional.of(new Merge(id, target.id, at));
    }

    /**
     * handle 변경(계정 탐험가만). 같은 handle 이면 빈 값. 다른 탐험가가 쓰거나 예약한 handle 이면 HANDLE_TAKEN.
     * 놓은 handle 은 reservation 기간 동안 이 탐험가 몫으로 예약된다(QA P3-10 — 공유된 옛 링크 탈취 방지, 본인은 되돌릴 수 있음).
     * @param taken 저장소 확인(이 탐험가 자신의 예약은 막지 않는다 — 동시 변경은 DB UNIQUE 가 막는다)
     */
    public Optional<HandleChange> changeHandle(Handle next, Instant at, Duration reservation, Predicate<Handle> taken) {
        requireActive();
        if (account == null) throw ExplorationError.LOGIN_REQUIRED.exception();
        if (next.equals(handle)) return Optional.empty();
        if (taken.test(next)) throw ExplorationError.HANDLE_TAKEN.exception(next.value());
        Handle previous = handle;
        this.handle = next;
        reservations.release(previous, next, at, reservation);
        return Optional.of(new HandleChange(id, previous, next, at));
    }

    // ---- 판정 ----------------------------------------------------------------------------------------------

    /** 계정에 그냥 연결할 수 있는 탐험가인지(활성 익명). */
    public boolean linkable() {
        return active() && account == null;
    }

    /** 이 탐험가를 target 으로 병합할 수 있는지(활성 익명 → 다른 활성 계정 탐험가). */
    public boolean canMergeInto(Explorer target) {
        return linkable() && !target.id.equals(id) && target.active() && target.account != null;
    }

    /** 공개 프로필(/u/{handle})을 가질 수 있는 탐험가 — 계정 연결된 활성 탐험가. */
    public boolean publicProfile() {
        return active() && account != null;
    }

    public boolean active() {
        return status == ExplorerStatus.ACTIVE;
    }

    /** 계정이 없는 탐험가(1~3단계 의미 그대로 — 응답의 anonymous). */
    public boolean anonymous() {
        return account == null;
    }

    public ExplorerId id() { return id; }
    public Handle handle() { return handle; }
    public AccessTokenHash tokenHash() { return tokenHash; }
    public Instant createdAt() { return createdAt; }
    public Optional<Account> account() { return Optional.ofNullable(account); }
    public ExplorerStatus status() { return status; }
    public Optional<ExplorerId> mergedInto() { return Optional.ofNullable(mergedInto); }
    public Instant mergedAt() { return mergedAt; }
    /** 이 탐험가가 놓은 handle 의 예약(저장용). */
    public List<HandleReservation> handleReservations() { return reservations.asList(); }

    private void requireActive() {
        if (!active()) throw ExplorationError.EXPLORER_NOT_FOUND.exception();
    }

    /** handle 이 정해지거나 바뀐 사실(공개 이벤트 HandleChanged 로 변환). */
    public record HandleChange(ExplorerId explorerId, Handle previous, Handle current, Instant at) {}

    /** 병합 사실(공개 이벤트 ExplorerMerged 로 변환). */
    public record Merge(ExplorerId from, ExplorerId into, Instant at) {}
}
