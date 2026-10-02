package com.kobi.territory;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.kobi.territory", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_depends_only_on_common =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..api..", "..application..", "..infra..",
                                "org.springframework.web..",
                                "jakarta.persistence..")
            .allowEmptyShould(true);

    // allowEmptyShould(true): 뼈대 단계에는 도메인 클래스가 없어(package-info 만 존재) 규칙이 검사할
    // 클래스가 0개다. ArchUnit 1.x 기본값(failOnEmptyShould=true)이면 빈 규칙이 실패하므로 규칙별로 허용한다.
    //
    // 모듈 간 참조 금지: 금지 목록은 "자신과 catalog를 제외한 나머지 도메인 모듈 전부".
    // (catalog는 exploration·wardrobe가 읽기 전용으로 참조할 수 있으므로 금지 목록에서 뺀다.)
    // 주의: 뼈대 단계용 전면 금지 규칙이다. 1단계부터는 implement-context 스킬의
    // "의존 규칙의 진화"에 따라 "..{context}.api.. 를 제외한 타 컨텍스트 참조 금지" 형태로 바꾼다.

    @ArchTest
    static final ArchRule exploration_talks_only_through_common =
        noClasses().that().resideInAPackage("com.kobi.territory.exploration..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.kobi.territory.progression..",
                                "com.kobi.territory.wardrobe..",
                                "com.kobi.territory.social..",
                                "com.kobi.territory.sharing..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule progression_talks_only_through_common =
        noClasses().that().resideInAPackage("com.kobi.territory.progression..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.kobi.territory.exploration..",
                                "com.kobi.territory.wardrobe..",
                                "com.kobi.territory.social..",
                                "com.kobi.territory.sharing..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule wardrobe_talks_only_through_common =
        noClasses().that().resideInAPackage("com.kobi.territory.wardrobe..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.kobi.territory.exploration..",
                                "com.kobi.territory.progression..",
                                "com.kobi.territory.social..",
                                "com.kobi.territory.sharing..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule social_talks_only_through_common =
        noClasses().that().resideInAPackage("com.kobi.territory.social..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.kobi.territory.exploration..",
                                "com.kobi.territory.progression..",
                                "com.kobi.territory.wardrobe..",
                                "com.kobi.territory.sharing..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule sharing_talks_only_through_common =
        noClasses().that().resideInAPackage("com.kobi.territory.sharing..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.kobi.territory.exploration..",
                                "com.kobi.territory.progression..",
                                "com.kobi.territory.wardrobe..",
                                "com.kobi.territory.social..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule catalog_talks_only_through_common =
        noClasses().that().resideInAPackage("com.kobi.territory.catalog..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.kobi.territory.exploration..",
                                "com.kobi.territory.progression..",
                                "com.kobi.territory.wardrobe..",
                                "com.kobi.territory.social..",
                                "com.kobi.territory.sharing..")
            .allowEmptyShould(true);
}
