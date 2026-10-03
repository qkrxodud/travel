package com.kobi.territory;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * QA P2-1 회귀: D7 경계 규칙이 허용 목록이라 api 루트 패키지(exploration.api.Leak) 참조도 잡는지, 테스트 전용 픽스처
 * (com.kobi.territory.archfixture — 실제 컨텍스트 구조를 흉내 낸 패키지: 탐험 api 루트의 Leak·api.event 의 Fine·domain 의 Secret,
 * 진행 application 의 Consumer)에 같은 규칙을 적용해 확인한다.
 */
@DisplayName("모듈 경계 규칙은 실제 위반을 잡는다")
class ArchitectureRuleRegressionTest {

    private static final String FIXTURE_ROOT = "com.kobi.territory.archfixture.";
    private static final JavaClasses FIXTURES = new ClassFileImporter().importPackages("com.kobi.territory.archfixture");

    private static EvaluationResult otherContextLooksIntoExploration() {
        return ArchitectureTest.onlyPublicContractVisible(FIXTURE_ROOT, "exploration",
            new String[] {"exploration", "progression"}).evaluate(FIXTURES);
    }

    @Nested
    @DisplayName("다른 컨텍스트가 탐험을 들여다볼 때")
    class LookingAcrossContexts {

        @Test
        @DisplayName("공개 이벤트·조회 계약이 아닌 공개 창구 바로 아래의 것을 보면 경계 위반으로 잡힌다")
        void apiRootReferenceIsViolation() {
            EvaluationResult result = otherContextLooksIntoExploration();
            assertThat(result.hasViolation()).isTrue();
            assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("Leak"));
        }

        @Test
        @DisplayName("공개 이벤트를 보는 것은 위반이 아니다")
        void publicEventReferenceIsAllowed() {
            assertThat(otherContextLooksIntoExploration().getFailureReport().getDetails())
                .noneMatch(detail -> detail.contains("Fine"));
        }
    }

    @Nested
    @DisplayName("공개 창구의 모양")
    class ApiShape {

        @Test
        @DisplayName("이벤트·조회·화면 세 갈래 밖에 무엇을 두면 위반으로 잡힌다")
        void extraPackageIsViolation() {
            assertThat(ArchitectureTest.apiHasOnlyEventQueryWeb(FIXTURE_ROOT, "exploration").evaluate(FIXTURES).hasViolation())
                .isTrue();
        }

        @Test
        @DisplayName("도메인을 모르는 공개 이벤트는 공개 계약 독립 규칙에 걸리지 않는다")
        void standaloneEventPasses() {
            assertThat(ArchitectureTest.publicContractStandalone(FIXTURE_ROOT, "exploration").evaluate(FIXTURES).hasViolation())
                .isFalse();
        }
    }
}
