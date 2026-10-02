package com.kobi.territory;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

/**
 * QA P2-1 회귀: D7 경계 규칙이 허용 목록이라 api 루트 패키지(exploration.api.Leak) 참조도 잡는지, 테스트 전용 픽스처
 * (com.kobi.territory.archfixture — 실제 컨텍스트 구조를 흉내 낸 패키지)에 같은 규칙을 적용해 확인한다.
 */
class ArchitectureRuleRegressionTest {

    private static final String FIXTURE_ROOT = "com.kobi.territory.archfixture.";
    private static final JavaClasses FIXTURES = new ClassFileImporter().importPackages("com.kobi.territory.archfixture");

    @Test
    void 다른_컨텍스트가_api_루트_클래스를_참조하면_실패하고_api_event_는_허용된다() {
        EvaluationResult result = ArchitectureTest.onlyPublicContractVisible(FIXTURE_ROOT, "exploration",
            new String[] {"exploration", "progression"}).evaluate(FIXTURES);
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("Leak"))
            .noneMatch(detail -> detail.contains("Fine"));
    }

    @Test
    void api_아래에_event_query_web_이외_패키지를_두면_실패한다() {
        assertThat(ArchitectureTest.apiHasOnlyEventQueryWeb(FIXTURE_ROOT, "exploration").evaluate(FIXTURES).hasViolation())
            .isTrue();
    }

    @Test
    void 공개_계약이_아닌_api_클래스가_도메인을_참조하는_것도_공개_범위_규칙이_잡는다() {
        assertThat(ArchitectureTest.publicContractStandalone(FIXTURE_ROOT, "exploration").evaluate(FIXTURES).hasViolation())
            .as("event 패키지의 Fine 은 도메인을 참조하지 않으므로 standalone 위반은 없다").isFalse();
    }
}
