package com.kobi.territory;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;

/**
 * 모듈 경계 규칙(implement-context "의존 규칙의 진화" — 1단계부터 api-예외 형태, 2단계 D7 로 api 를 셋으로 나눔).
 * <ol>
 *   <li>domain 순수성: domain은 java·common·자기 domain만 참조(Spring·JPA·api·application·infra 금지)</li>
 *   <li>공개 범위(D7): 다른 컨텍스트는 X의 {@code api.event}(공개 이벤트)·{@code api.query}(Query 인터페이스·DTO)만 참조할 수
 *       있다 — X.domain/application/infra 와 X.api.web(컨트롤러·웹 DTO)은 금지</li>
 *   <li>공개 계약의 독립: X.api.event·X.api.query 는 자기 domain·application·infra·api.web 을 참조하지 않는다
 *       (도메인 타입이 공개 계약으로 새지 않게)</li>
 *   <li>의존 매트릭스: 허용되지 않은 컨텍스트는 api조차 참조 금지(domain-model.md §1)</li>
 * </ol>
 * Gradle 의존(build.gradle)과 함께 갱신한다. 둘 중 하나만 고치면 컴파일 불가 또는 이 테스트 실패.
 * <p>
 * allowEmptyShould(true)는 아직 클래스가 없는 모듈(wardrobe·social·sharing)이 주어인 규칙에만 붙인다.
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
    static final ArchRule progression_domain_depends_only_on_common = domainOnlyOnCommon("progression");

    @ArchTest
    static final ArchRule common_has_no_web_jpa_or_context =
        noClasses().that().resideInAPackage(ROOT + "common..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.web..", "jakarta.persistence..", "org.springframework.data..",
                                ROOT + "catalog..", ROOT + "exploration..", ROOT + "progression..",
                                ROOT + "wardrobe..", ROOT + "social..", ROOT + "sharing..");

    // ---- 2. 다른 컨텍스트는 api.event·api.query 만 (D7) ----------------------------------------

    @ArchTest static final ArchRule catalog_internals_are_private = onlyPublicContractVisible(ROOT, "catalog", CONTEXTS);
    @ArchTest static final ArchRule exploration_internals_are_private = onlyPublicContractVisible(ROOT, "exploration", CONTEXTS);
    @ArchTest static final ArchRule progression_internals_are_private = onlyPublicContractVisible(ROOT, "progression", CONTEXTS);
    @ArchTest static final ArchRule wardrobe_internals_are_private = onlyPublicContractVisible(ROOT, "wardrobe", CONTEXTS);
    @ArchTest static final ArchRule social_internals_are_private = onlyPublicContractVisible(ROOT, "social", CONTEXTS);
    @ArchTest static final ArchRule sharing_internals_are_private = onlyPublicContractVisible(ROOT, "sharing", CONTEXTS);

    // ---- 3. 공개 계약(api.event·api.query)은 자기 내부를 모른다 + api 아래엔 event·query·web 만 (D7) -------------

    @ArchTest static final ArchRule catalog_public_contract_is_standalone = publicContractStandalone(ROOT, "catalog");
    @ArchTest static final ArchRule exploration_public_contract_is_standalone = publicContractStandalone(ROOT, "exploration");
    @ArchTest static final ArchRule progression_public_contract_is_standalone = publicContractStandalone(ROOT, "progression");
    @ArchTest static final ArchRule catalog_api_has_only_event_query_web = apiHasOnlyEventQueryWeb(ROOT, "catalog");
    @ArchTest static final ArchRule exploration_api_has_only_event_query_web = apiHasOnlyEventQueryWeb(ROOT, "exploration");
    @ArchTest static final ArchRule progression_api_has_only_event_query_web = apiHasOnlyEventQueryWeb(ROOT, "progression");

    // ---- 3-1. infra 하위(entity·repository)는 같은 컨텍스트 infra 안에서만 (infra 하위 패키지 규칙) -------------

    @ArchTest static final ArchRule catalog_infra_is_internal = infraInternal(ROOT, "catalog");
    @ArchTest static final ArchRule exploration_infra_is_internal = infraInternal(ROOT, "exploration");
    @ArchTest static final ArchRule progression_infra_is_internal = infraInternal(ROOT, "progression");

    // ---- 4. 의존 매트릭스(허용되지 않은 컨텍스트는 api도 금지) -----------------------------

    @ArchTest
    static final ArchRule catalog_is_upstream_of_all =
        forbid("catalog", "exploration", "progression", "wardrobe", "social", "sharing");

    @ArchTest
    static final ArchRule exploration_depends_only_on_common_and_catalog =
        forbid("exploration", "progression", "wardrobe", "social", "sharing");

    @ArchTest
    static final ArchRule progression_matrix =
        forbid("progression", "wardrobe", "social", "sharing");

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

    /**
     * 허용 목록(QA P2-1): X 밖의 컨텍스트 모듈이 참조할 수 있는 X 의 클래스는 X.api.event·X.api.query 뿐이다.
     * X.api 루트·X.api.web·그 밖의 하위 패키지·domain·application·infra 는 모두 금지(app-api 는 조립 모듈이라 제외).
     */
    static ArchRule onlyPublicContractVisible(String root, String context, String[] contexts) {
        String[] others = Arrays.stream(contexts).filter(other -> !other.equals(context))
            .map(other -> root + other + "..").toArray(String[]::new);
        return noClasses().that().resideInAnyPackage(others)
            .should().dependOnClassesThat(outsidePublicContract(root, context))
            .as("다른 컨텍스트는 " + context + ".api.event·api.query 만 참조한다");
    }

    /** X.api.event·X.api.query 는 X 의 공개 계약 밖(루트 api·web·domain·application·infra 등)을 참조하지 않는다. */
    static ArchRule publicContractStandalone(String root, String context) {
        String prefix = root + context;
        return noClasses().that().resideInAnyPackage(prefix + ".api.event..", prefix + ".api.query..")
            .should().dependOnClassesThat(outsidePublicContract(root, context))
            .as(context + " 의 공개 계약(api.event·api.query)은 자기 내부·웹 계층을 참조하지 않는다");
    }

    /** X.api 아래에는 event·query·web 하위 패키지만 둔다(package-info 제외). */
    static ArchRule apiHasOnlyEventQueryWeb(String root, String context) {
        String prefix = root + context;
        return classes().that().resideInAPackage(prefix + ".api..").and().doNotHaveSimpleName("package-info")
            .should().resideInAnyPackage(prefix + ".api.event..", prefix + ".api.query..", prefix + ".api.web..")
            .as(context + ".api 아래에는 event·query·web 만 둔다");
    }

    /**
     * infra/entity·infra/repository 는 패키지가 갈려 public 이 된 타입이 많다(엔티티·변환 메서드·Spring Data 인터페이스).
     * 그 대신 같은 컨텍스트의 infra 밖(application·api·domain·다른 컨텍스트·app-api)에서는 참조하지 못하게 막는다.
     */
    static ArchRule infraInternal(String root, String context) {
        String prefix = root + context;
        return classes().that().resideInAnyPackage(prefix + ".infra.entity..", prefix + ".infra.repository..")
            .should().onlyHaveDependentClassesThat().resideInAPackage(prefix + ".infra..")
            .as(context + ".infra.entity·infra.repository 는 같은 컨텍스트 infra 안에서만 참조한다");
    }

    private static DescribedPredicate<JavaClass> outsidePublicContract(String root, String context) {
        String prefix = root + context;
        return JavaClass.Predicates.resideInAPackage(prefix + "..")
            .and(DescribedPredicate.not(JavaClass.Predicates.resideInAnyPackage(prefix + ".api.event..", prefix + ".api.query..")))
            .as(context + " 의 api.event·api.query 밖 클래스");
    }

    private static ArchRule forbid(String context, String... forbidden) {
        String[] pkgs = Arrays.stream(forbidden).map(other -> ROOT + other + "..").toArray(String[]::new);
        return noClasses().that().resideInAPackage(ROOT + context + "..")
            .should().dependOnClassesThat().resideInAnyPackage(pkgs)
            .as(context + " 은 " + String.join("·", forbidden) + " 을 참조하지 않는다");
    }
}
