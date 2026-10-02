package com.kobi.territory.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 무결성_위반은_제약_이름과_무관하게_범용_CONFLICT_도메인_번역은_컨텍스트_infra_몫() {
        // visit UNIQUE → DUPLICATE_VISIT 번역은 exploration infra(JpaTerritoryRepository)로 옮겼다(QA N3)
        var mysql = new DataIntegrityViolationException("x", new SQLIntegrityConstraintViolationException(
            "Duplicate entry 'm-KR-11010-e' for key 'visit.uq_visit_map_region_member'"));
        assertThat(handler.handle(mysql).getBody().code()).isEqualTo("CONFLICT");
        var other = new DataIntegrityViolationException("x", new RuntimeException("uq_explorer_handle"));
        var res = handler.handle(other);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    void 낙관적_락_충돌은_409_CONCURRENT_UPDATE() {
        var res = handler.handle(new ObjectOptimisticLockingFailureException("QuestRow", "k"));
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().code()).isEqualTo("CONCURRENT_UPDATE");
    }
}
