package com.kobi.territory;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;

/**
 * 모듈 경계 규칙(implement-context "의존 규칙의 진화" — 1단계부터 api-예외 형태).
 * <ol>
 *   <li>domain 순수성: domain은 java·common·자기 domain만 참조(Spring·JPA·api·application·infra 금지)</li>
 *   <li>api-only: 다른 컨텍스트는 X의 api 패키지만 참조할 수 있다(X.domain/application/infra 금지)</li>
 *   <li>의존 매트릭스: 허용되지 않은 컨텍스트는 api조차 참조 금지(domain-model.md §1)</li>
 * </ol>
 * Gradle 의존(build.gradle)과 함께 갱신한다. 둘 중 하나만 고치면 컴파일 불가 또는 이 테스트 실패.
 * <p>
 * allowEmptyShould(true)는 아직 클래스가 없는 모듈(progression·wardrobe·social·sharing)이 주어인 규칙에만 붙인다.
 * 클래스가 생기는 단계에서 제거한다(패키지 오타가 조용히 통과하지 않게).
 */
@AnalyzeClasses(packages = "com.kobi.territory", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.kobi.territory.";
    private static final String[] CONTEXTS = {"catalog", "exploration", "progression", "wardrobe", "social", "sharing"};

    // ---- 1. domain 순수성 ---------------------------------------------------------------

    @ArchTest
    static final ArchRule domain_has_no_framework_or_outer_layer =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..api..", "..application..", "..infra..",
                                "org.springframework..",
                                "jakarta.persistence..",
                                "com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule catalog_domain_depends_only_on_common = domainOnlyOnCommon("catalog");

    @ArchTest
    static final ArchRule exploration_domain_depends_only_on_common = domainOnlyOnCommon("exploration");

    @ArchTest
    static final ArchRule common_has_no_web_jpa_or_context =
        noClasses().that().resideInAPackage(ROOT + "common..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.web..", "jakarta.persistence..", "org.springframework.data..",
                                ROOT + "catalog..", ROOT + "exploration..", ROOT + "progression..",
                                ROOT + "wardrobe..", ROOT + "social..", ROOT + "sharing..");

    // ---- 2. 다른 컨텍스트는 api 패키지만 ---------------------------------------------------

    @ArchTest static final ArchRule catalog_internals_are_private = onlyApiVisible("catalog");
    @ArchTest static final ArchRule exploration_internals_are_private = onlyApiVisible("exploration");
    @ArchTest static final ArchRule progression_internals_are_private = onlyApiVisible("progression");
    @ArchTest static final ArchRule wardrobe_internals_are_private = onlyApiVisible("wardrobe");
    @ArchTest static final ArchRule social_internals_are_private = onlyApiVisible("social");
    @ArchTest static final ArchRule sharing_internals_are_private = onlyApiVisible("sharing");

    // ---- 3. 의존 매트릭스(허용되지 않은 컨텍스트는 api도 금지) -----------------------------

    @ArchTest
    static final ArchRule catalog_is_upstream_of_all =
        forbid("catalog", "exploration", "progression", "wardrobe", "social", "sharing");

    @ArchTest
    static final ArchRule exploration_depends_only_on_common_and_catalog =
        forbid("exploration", "progression", "wardrobe", "social", "sharing");

    @ArchTest
    static final ArchRule progression_matrix =
        forbid("progression", "wardrobe", "social", "sharing").allowEmptyShould(true);

    @ArchTest
    static final ArchRule wardrobe_matrix =
        forbid("wardrobe", "social", "sharing").allowEmptyShould(true);

    @ArchTest
    static final ArchRule social_matrix =
        forbid("social", "catalog", "wardrobe", "sharing").allowEmptyShould(true);

    // sharing: common, catalog, 모든 컨텍스트의 api 허용 → 규칙 2(api-only)로 충분.

    // ---- helpers ----------------------------------------------------------------------

    private static ArchRule domainOnlyOnCommon(String context) {
        return classes().that().resideInAPackage(ROOT + context + ".domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", ROOT + "common..", ROOT + context + ".domain..")
            .as(context + ".domain 은 java·common·자기 domain 만 참조한다");
    }

    /** X 밖의 컨텍스트 모듈은 X의 domain/application/infra 를 참조할 수 없다(app-api 는 조립 모듈이라 제외). */
    private static ArchRule onlyApiVisible(String context) {
        String[] others = Arrays.stream(CONTEXTS).filter(c -> !c.equals(context))
            .map(c -> ROOT + c + "..").toArray(String[]::new);
        return noClasses().that().resideInAnyPackage(others)
            .should().dependOnClassesThat()
            .resideInAnyPackage(ROOT + context + ".domain..", ROOT + context + ".application..", ROOT + context + ".infra..")
            .as("다른 컨텍스트는 " + context + ".api 만 참조한다");
    }

    private static ArchRule forbid(String context, String... forbidden) {
        String[] pkgs = Arrays.stream(forbidden).map(c -> ROOT + c + "..").toArray(String[]::new);
        return noClasses().that().resideInAPackage(ROOT + context + "..")
            .should().dependOnClassesThat().resideInAnyPackage(pkgs)
            .as(context + " 은 " + String.join("·", forbidden) + " 을 참조하지 않는다");
    }
}
