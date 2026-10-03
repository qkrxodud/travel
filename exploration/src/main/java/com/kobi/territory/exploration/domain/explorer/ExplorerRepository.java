package com.kobi.territory.exploration.domain.explorer;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Explorer 애그리거트 저장소 — explorer 행 + 자식 account 행. */
public interface ExplorerRepository {

    /** 탐험가(와 새로 연결된 계정)를 저장한다. */
    void save(Explorer explorer);

    Optional<Explorer> findById(ExplorerId id);

    /**
     * 탐험가 행을 배타 잠금(SELECT … FOR UPDATE)하고 최신 상태로 불러온다 — 로그인(연결·병합)·handle 변경은 이것으로 시작한다.
     * 같은 트랜잭션에서 먼저 읽은 사본이 있어도 잠금 조회 결과로 새로 고친다.
     */
    Optional<Explorer> findLocked(ExplorerId id);

    /**
     * 탐험가 행을 공유 잠금(SELECT … FOR SHARE)하고 최신 상태로 불러온다 — 체크인·지도 커맨드가 지도/territory 를 잠근 뒤 "아직 활성인가"를
     * 확인할 때 쓴다. 병합(배타 잠금)과 직렬화돼, 병합이 커밋된 뒤의 체크인·합류는 비활성을 보고 거절되고, 병합보다 먼저 잠근 체크인·합류는
     * 병합이 그 커밋을 기다려 읽는다(병합 중 체크인 경합).
     */
    Optional<Explorer> findLockedShared(ExplorerId id);

    /** 이 신원(provider, subject)이 연결된 탐험가. */
    Optional<Explorer> findByAccount(String provider, String subject);

    /** 토큰 해시로 탐험가 id 찾기(인증). */
    Optional<ExplorerId> findIdByTokenHash(AccessTokenHash tokenHash);

    /** handle 로 탐험가 찾기. */
    Optional<Explorer> findByHandle(Handle handle);

    /**
     * requester 가 아닌 탐험가가 이 handle 을 쓰고 있거나 now 기준으로 예약 중인지(자동 발급·변경 전 확인 — 동시 발급은 DB UNIQUE 가
     * 막는다).
     */
    boolean handleTaken(Handle handle, ExplorerId requester, Instant now);

    /** 활성 탐험가 id(재계산 배치용 — 병합돼 비활성인 탐험가 제외). */
    List<ExplorerId> activeIds();
}
