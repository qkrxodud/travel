package com.kobi.territory.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** 공통 오류 응답. 회귀 출처: 1단계 QA N3(방문 중복 번역은 탐험 infra 로 옮김). */
@DisplayName("겹친 요청의 오류 안내")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("이미 있는 기록과 겹치면 어느 규칙에 걸렸든 일반 충돌로 알린다 — 도메인 말로 바꾸는 일은 각 컨텍스트가 한다")
    void integrityViolationIsGenericConflict() {
        var mysql = new DataIntegrityViolationException("x", new SQLIntegrityConstraintViolationException(
            "Duplicate entry 'm-KR-11010-e' for key 'visit.uq_visit_map_region_member'"));
        assertThat(handler.handle(mysql).getBody().code()).isEqualTo("CONFLICT");
        var other = new DataIntegrityViolationException("x", new RuntimeException("uq_explorer_handle"));
        var res = handler.handle(other);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    @DisplayName("같은 기록을 동시에 고쳐 저장이 겹치면 다시 시도하라고 알린다")
    void concurrentUpdateAsksToRetry() {
        var res = handler.handle(new ObjectOptimisticLockingFailureException("QuestRow", "k"));
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().code()).isEqualTo("CONCURRENT_UPDATE");
    }
}
