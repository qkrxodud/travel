package com.kobi.territory.exploration.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.ExplorationException;
import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** 같은 지역을 동시에 두 번 칠한 경우의 저장소 번역(MySQL·H2 메시지). 회귀 출처: QA N3. */
@DisplayName("같은 지역을 동시에 두 번 칠할 때")
class DuplicateVisitTranslationTest {

    @Test
    @DisplayName("운영 데이터베이스에서 겹치면 이미 칠한 지역이라고 거절된다")
    void mysql() {
        var mysql = new DataIntegrityViolationException("x", new SQLIntegrityConstraintViolationException(
            "Duplicate entry 'm-KR-11010-e' for key 'visit.uq_visit_map_region_member'"));
        assertThat(JpaTerritoryRepository.translate(mysql)).isInstanceOf(ExplorationException.class)
            .satisfies(exception -> assertThat(((ExplorationException) exception).error()).isEqualTo(ExplorationError.DUPLICATE_VISIT));
    }

    @Test
    @DisplayName("로컬 데이터베이스에서 겹쳐도 이미 칠한 지역이라고 거절된다")
    void h2() {
        var h2 = new DataIntegrityViolationException("x", new RuntimeException(
            "Unique index or primary key violation: \"PUBLIC.UQ_VISIT_MAP_REGION_MEMBER_INDEX_4 ON PUBLIC.VISIT\""));
        assertThat(((ExplorationException) JpaTerritoryRepository.translate(h2)).error()).isEqualTo(ExplorationError.DUPLICATE_VISIT);
    }

    @Test
    @DisplayName("다른 저장 실패는 중복 방문으로 바꾸지 않는다")
    void otherFailure() {
        var other = new DataIntegrityViolationException("x", new RuntimeException("fk_visit_explorer"));
        assertThat(JpaTerritoryRepository.translate(other)).isSameAs(other);
    }
}
