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
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** QA P3-13: 동시에 같은 handle 로 바꾸면 explorer.handle UNIQUE 위반 → HANDLE_TAKEN(409) 으로 번역(MySQL·H2 메시지). */
class HandleTakenTranslationTest {

    static final Explorer KIM = Explorer.restore(ExplorerId.of("11111111-1111-1111-1111-111111111111"), new Handle("kim"), null,
        Instant.EPOCH, new Account(new AccountIdentity("google", "sub", "kim@example.com"), Instant.EPOCH), ExplorerStatus.ACTIVE,
        null, null, List.of());

    @Test
    void handle_UNIQUE_위반은_HANDLE_TAKEN() {
        var mysql = new DataIntegrityViolationException("x", new SQLIntegrityConstraintViolationException(
            "Duplicate entry 'kim' for key 'explorer.uq_explorer_handle'"));
        assertThat(((ExplorationException) JpaExplorerRepository.translate(mysql, KIM)).error()).isEqualTo(ExplorationError.HANDLE_TAKEN);
        var h2 = new DataIntegrityViolationException("x", new RuntimeException(
            "Unique index or primary key violation: \"PUBLIC.UQ_EXPLORER_HANDLE_INDEX_8 ON PUBLIC.EXPLORER(HANDLE)\""));
        assertThat(((ExplorationException) JpaExplorerRepository.translate(h2, KIM)).error()).isEqualTo(ExplorationError.HANDLE_TAKEN);
    }

    @Test
    void 계정_UNIQUE_위반은_그대로_던진다_로그인이_재시도() {
        var account = new DataIntegrityViolationException("x", new RuntimeException("uq_account_identity"));
        assertThat(JpaExplorerRepository.translate(account, KIM)).isSameAs(account);
    }
}
