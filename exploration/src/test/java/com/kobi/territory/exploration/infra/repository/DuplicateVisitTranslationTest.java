package com.kobi.territory.exploration.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.ExplorationException;
import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** QA N3: visit UNIQUE 위반 → 도메인 오류 DUPLICATE_VISIT 번역은 exploration infra 가 한다(MySQL·H2 메시지). */
class DuplicateVisitTranslationTest {

    @Test
    void visit_UNIQUE_위반은_DUPLICATE_VISIT_로_번역한다() {
        var mysql = new DataIntegrityViolationException("x", new SQLIntegrityConstraintViolationException(
            "Duplicate entry 'm-KR-11010-e' for key 'visit.uq_visit_map_region_member'"));
        assertThat(JpaTerritoryRepository.translate(mysql)).isInstanceOf(ExplorationException.class)
            .satisfies(exception -> assertThat(((ExplorationException) exception).error()).isEqualTo(ExplorationError.DUPLICATE_VISIT));

        var h2 = new DataIntegrityViolationException("x", new RuntimeException(
            "Unique index or primary key violation: \"PUBLIC.UQ_VISIT_MAP_REGION_MEMBER_INDEX_4 ON PUBLIC.VISIT\""));
        assertThat(((ExplorationException) JpaTerritoryRepository.translate(h2)).error())
            .isEqualTo(ExplorationError.DUPLICATE_VISIT);
    }

    @Test
    void 그_밖의_무결성_위반은_그대로_던진다() {
        var other = new DataIntegrityViolationException("x", new RuntimeException("fk_visit_explorer"));
        assertThat(JpaTerritoryRepository.translate(other)).isSameAs(other);
    }
}
