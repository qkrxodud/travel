package com.kobi.territory.exploration.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.ExplorationException;
import com.kobi.territory.exploration.domain.explorer.Account;
import com.kobi.territory.exploration.domain.explorer.AccountIdentity;
import com.kobi.territory.exploration.domain.explorer.Explorer;
import com.kobi.territory.exploration.domain.explorer.ExplorerStatus;
import com.kobi.territory.exploration.domain.explorer.Handle;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** 두 탐험가가 동시에 같은 핸들로 바꾼 경우의 저장소 번역(MySQL·H2 메시지). 회귀 출처: QA P3-13. */
@DisplayName("두 탐험가가 동시에 같은 handle로 바꿀 때")
class HandleTakenTranslationTest {

    static final Explorer KIM = Explorer.restore(ExplorerId.of("11111111-1111-1111-1111-111111111111"), new Handle("kim"), null,
        Instant.EPOCH, new Account(new AccountIdentity("google", "sub", "kim@example.com"), Instant.EPOCH), ExplorerStatus.ACTIVE,
        null, null, List.of());

    @Test
    @DisplayName("운영 데이터베이스에서 겹치면 늦은 쪽은 이미 쓰는 handle이라고 거절된다")
    void mysql() {
        var mysql = new DataIntegrityViolationException("x", new SQLIntegrityConstraintViolationException(
            "Duplicate entry 'kim' for key 'explorer.uq_explorer_handle'"));
        assertThat(((ExplorationException) JpaExplorerRepository.translate(mysql, KIM)).error()).isEqualTo(ExplorationError.HANDLE_TAKEN);
    }

    @Test
    @DisplayName("로컬 데이터베이스에서 겹쳐도 이미 쓰는 handle이라고 거절된다")
    void h2() {
        var h2 = new DataIntegrityViolationException("x", new RuntimeException(
            "Unique index or primary key violation: \"PUBLIC.UQ_EXPLORER_HANDLE_INDEX_8 ON PUBLIC.EXPLORER(HANDLE)\""));
        assertThat(((ExplorationException) JpaExplorerRepository.translate(h2, KIM)).error()).isEqualTo(ExplorationError.HANDLE_TAKEN);
    }

    @Test
    @DisplayName("같은 계정의 동시 첫 로그인 충돌은 바꾸지 않고 넘겨 로그인이 다시 시도한다")
    void accountConflictPassesThrough() {
        var account = new DataIntegrityViolationException("x", new RuntimeException("uq_account_identity"));
        assertThat(JpaExplorerRepository.translate(account, KIM)).isSameAs(account);
    }
}
