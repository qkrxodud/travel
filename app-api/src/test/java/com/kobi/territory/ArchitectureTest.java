package com.kobi.territory;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 모듈 경계 규칙(implement-context "의존 규칙의 진화" — 1단계부터 api-예외 형태, 2단계 D7 로 api 를 셋으로 나눔).
 * <ol>
 *   <li>domain 순수성: domain은 java·common·자기 domain만 참조(Spring·JPA·api·application·infra 금지)</li>
 *   <li>공개 범위(D7): 다른 컨텍스트는 X의 {@code api.event}(공개 이벤트)·{@code api.query}(Query 인터페이스·DTO)만 참조할 수
 *       있다 — X.domain/application/infra 와 X.api.web(컨트롤러·웹 DTO)은 금지(허용 목록 방식 — QA P2-1)</li>
 *   <li>공개 계약의 독립: X.api.event·X.api.query 는 자기 domain·application·infra·api.web 을 참조하지 않는다
 *       (도메인 타입이 공개 계약으로 새지 않게). X.api 아래엔 event·query·web 만.</li>
 *   <li>infra/entity·infra/repository 는 같은 컨텍스트 infra 안에서만</li>
 *   <li>의존 매트릭스: 허용되지 않은 컨텍스트는 api조차 참조 금지(domain-model.md §1)</li>
 * </ol>
 * Gradle 의존(build.gradle)과 함께 갱신한다. 둘 중 하나만 고치면 컴파일 불가 또는 이 테스트 실패.
 * <p>
 * 규칙은 ArchUnit 의 기본 설정(빈 대상이면 실패 — failOnEmptyShould)으로 검사한다. 패키지 오타가 조용히 통과하지 않게
 * allowEmptyShould(true)는 쓰지 않는다. 7단계: 보고서가 규칙 문장으로 읽히도록 @ArchTest 필드 대신 JUnit @Nested·@DisplayName 으로 묶었다
 * (규칙 38개는 그대로 — 컨텍스트별 규칙은 컨텍스트마다 한 건). 10단계: 분석(analytics) 컨텍스트 추가 — 관찰자라 아무도 참조하지 않고,
 * 분석은 탐험·진행의 공개 이벤트만 본다. 12단계: 알림(notification) 컨텍스트 추가 — 게임 컨텍스트는 알림을 모르고, 알림은 카탈로그·진행의
 * 조회 계약과 탐험의 공개 이벤트만 본다. 분석은 알림의 공개 이벤트(PushSent)를 관찰한다.
 */
@DisplayName("모듈 경계")
class ArchitectureTest {

    private static final String ROOT = "com.kobi.territory.";
    private static final String[] CONTEXTS = {"catalog", "exploration", "progression", "wardrobe", "social", "sharing", "analytics",
        "notification"};

    /** 운영 코드만(테스트 픽스처 제외) 한 번 읽어 모든 규칙이 함께 쓴다. */
    private static final JavaClasses PRODUCTION = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.kobi.territory");

    /** 바운디드 컨텍스트 — 보고서에는 한글 이름으로 나온다. */
    enum Context {
        CATALOG("catalog", "카탈로그"),
        EXPLORATION("exploration", "탐험"),
        PROGRESSION("progression", "진행"),
        WARDROBE("wardrobe", "꾸미기"),
        SOCIAL("social", "소셜"),
        SHARING("sharing", "공유"),
        ANALYTICS("analytics", "분석"),
        NOTIFICATION("notification", "알림");

        final String pkg;
        private final String label;

        Context(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @Nested
    @DisplayName("도메인 모델은 순수하다")
    class DomainPurity {

        @Test
        @DisplayName("어느 컨텍스트의 도메인 모델도 프레임워크·저장 기술·직렬화·바깥 계층을 모른다")
        void noFrameworkOrOuterLayer() {
            noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..api..", "..application..", "..infra..",
                                    "org.springframework..",
                                    "jakarta.persistence..",
                                    "com.fasterxml.jackson..")
                .check(PRODUCTION);
        }

        @ParameterizedTest(name = "{0}의 도메인 모델은 자기 도메인과 공유 커널만 안다")
        @EnumSource(Context.class)
        @DisplayName("도메인 모델은 자기 도메인과 공유 커널만 안다")
        void onlyOwnDomainAndKernel(Context context) {
            domainOnlyOnCommon(context.pkg).check(PRODUCTION);
        }

        @Test
        @DisplayName("공유 커널은 웹·저장 기술과 어떤 컨텍스트도 모른다")
        void kernelKnowsNoContext() {
            noClasses().that().resideInAPackage(ROOT + "common..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "jakarta.persistence..", "org.springframework.data..",
                                    ROOT + "catalog..", ROOT + "exploration..", ROOT + "progression..",
                                    ROOT + "wardrobe..", ROOT + "social..", ROOT + "sharing..", ROOT + "analytics..",
                                    ROOT + "notification..")
                .check(PRODUCTION);
        }
    }

    @Nested
    @DisplayName("다른 컨텍스트는 공개 이벤트와 조회 계약만 볼 수 있다")
    class PublicContractOnly {

        @ParameterizedTest(name = "{0}의 도메인·유스케이스·저장소·화면 계약은 다른 컨텍스트에 감춰진다")
        @EnumSource(Context.class)
        @DisplayName("내부는 다른 컨텍스트에 감춰진다")
        void internalsArePrivate(Context context) {
            onlyPublicContractVisible(ROOT, context.pkg, CONTEXTS).check(PRODUCTION);
        }
    }

    @Nested
    @DisplayName("공개 계약은 자기 내부를 드러내지 않는다")
    class PublicContractStandalone {

        /** 분석은 공개 이벤트·조회 계약이 없어(관찰자) 대상이 비므로 뺀다 — 대신 아래 "분석의 공개 창구는 화면뿐"이 지킨다. */
        @ParameterizedTest(name = "{0}의 공개 이벤트·조회 계약은 자기 도메인·유스케이스·저장소·화면 계약을 모른다")
        @EnumSource(value = Context.class, mode = EnumSource.Mode.EXCLUDE, names = "ANALYTICS")
        @DisplayName("공개 이벤트·조회 계약은 자기 내부를 모른다")
        void contractIsStandalone(Context context) {
            publicContractStandalone(ROOT, context.pkg).check(PRODUCTION);
        }

        @Test
        @DisplayName("분석은 공개 이벤트도 조회 계약도 내놓지 않는다 — 공개 창구는 화면(수집·지표)뿐이다")
        void analyticsPublishesNothing() {
            classes().that().resideInAPackage(ROOT + "analytics.api..").and().doNotHaveSimpleName("package-info")
                .should().resideInAPackage(ROOT + "analytics.api.web..")
                .as("analytics.api 아래에는 web 만 둔다")
                .check(PRODUCTION);
        }

        @ParameterizedTest(name = "{0}의 공개 창구에는 이벤트·조회·화면 세 갈래만 둔다")
        @EnumSource(Context.class)
        @DisplayName("공개 창구에는 이벤트·조회·화면 세 갈래만 둔다")
        void apiHasThreeBranches(Context context) {
            apiHasOnlyEventQueryWeb(ROOT, context.pkg).check(PRODUCTION);
        }
    }

    @Nested
    @DisplayName("저장 모델은 자기 컨텍스트의 저장 계층 안에서만 쓰인다")
    class StorageIsInternal {

        @ParameterizedTest(name = "{0}의 저장 모델과 저장소는 {0} 저장 계층 밖에서 보이지 않는다")
        @EnumSource(Context.class)
        @DisplayName("저장 모델과 저장소는 저장 계층 밖에서 보이지 않는다")
        void infraIsInternal(Context context) {
            infraInternal(ROOT, context.pkg).check(PRODUCTION);
        }
    }

    @Nested
    @DisplayName("컨텍스트는 정해진 방향으로만 서로를 안다")
    class DependencyMatrix {

        @Test
        @DisplayName("카탈로그는 맨 위에 있어 어떤 컨텍스트도 모른다")
        void catalogIsUpstream() {
            forbid("catalog", "exploration", "progression", "wardrobe", "social", "sharing").check(PRODUCTION);
        }

        @Test
        @DisplayName("탐험은 카탈로그만 안다")
        void explorationKnowsOnlyCatalog() {
            forbid("exploration", "progression", "wardrobe", "social", "sharing").check(PRODUCTION);
        }

        @Test
        @DisplayName("진행은 꾸미기·소셜·공유를 모른다")
        void progressionMatrix() {
            forbid("progression", "wardrobe", "social", "sharing").check(PRODUCTION);
        }

        /** wardrobe: common, catalog, exploration(api), progression(api) 허용(§1) — api 밖 참조는 공개 범위 규칙이 막는다. */
        @Test
        @DisplayName("꾸미기는 소셜·공유를 모른다")
        void wardrobeMatrix() {
            forbid("wardrobe", "social", "sharing").check(PRODUCTION);
        }

        /** social(5단계): common, exploration(api), progression(api) 허용(§1) — catalog·wardrobe·sharing 은 api 도 금지. */
        @Test
        @DisplayName("소셜은 카탈로그·꾸미기·공유를 모른다")
        void socialMatrix() {
            forbid("social", "catalog", "wardrobe", "sharing").check(PRODUCTION);
        }

        /** 10단계: 분석은 관찰자 — 게임 컨텍스트는 분석을 모른다(분석이 멈추거나 빠져도 게임 규칙은 그대로). */
        @ParameterizedTest(name = "{0}은 분석을 모른다")
        @EnumSource(value = Context.class, mode = EnumSource.Mode.EXCLUDE, names = "ANALYTICS")
        @DisplayName("어떤 게임 컨텍스트도 분석을 모른다 — 분석은 관찰자다")
        void nobodyKnowsAnalytics(Context context) {
            forbid(context.pkg, "analytics").check(PRODUCTION);
        }

        /**
         * 분석(§1 analytics): 프로젝트 안에서는 자기 자신·공유 커널·탐험과 진행(12단계: 알림도)의 공개 이벤트(api.event)만 참조한다(허용 목록 —
         * QA P3-6). 카탈로그·꾸미기·소셜·공유는 물론 탐험·진행의 조회 계약(api.query)·화면 계약·내부도 모른다.
         */
        @Test
        @DisplayName("분석은 공유 커널과 탐험·진행·알림이 내놓은 사실(공개 이벤트)만 안다")
        void analyticsKnowsOnlyPublishedFacts() {
            DescribedPredicate<JavaClass> outsideAllowed = JavaClass.Predicates.resideInAPackage("com.kobi.territory..")
                .and(DescribedPredicate.not(JavaClass.Predicates.resideInAnyPackage(ROOT + "analytics..", ROOT + "common..",
                    ROOT + "exploration.api.event..", ROOT + "progression.api.event..", ROOT + "notification.api.event..")))
                .as("analytics·common·exploration.api.event·progression.api.event·notification.api.event 밖의 프로젝트 클래스");
            noClasses().that().resideInAPackage(ROOT + "analytics..")
                .should().dependOnClassesThat(outsideAllowed)
                .as("analytics 는 자기 자신·common·exploration·progression·notification 의 api.event 만 참조한다")
                .check(PRODUCTION);
        }

        /** 12단계: 알림은 게임 규칙을 바꾸지 않는다 — 게임 컨텍스트는 알림을 모른다(알림이 멈추거나 빠져도 게임은 그대로). */
        @ParameterizedTest(name = "{0}은 알림을 모른다")
        @EnumSource(value = Context.class, mode = EnumSource.Mode.EXCLUDE, names = {"ANALYTICS", "NOTIFICATION"})
        @DisplayName("어떤 게임 컨텍스트도 알림을 모른다")
        void nobodyKnowsNotification(Context context) {
            forbid(context.pkg, "notification").check(PRODUCTION);
        }

        /**
         * 알림(§1 notification, 12단계): 자기 자신·공유 커널·카탈로그의 조회 계약(이번 주 미스터리·계절 정의)·진행의 조회 계약(스트릭)·탐험의 공개
         * 이벤트(계정 병합)만 참조한다(허용 목록). 꾸미기·소셜·공유·분석과 다른 컨텍스트의 내부는 모른다.
         */
        @Test
        @DisplayName("알림은 카탈로그·진행의 조회 계약과 탐험의 공개 이벤트만 안다")
        void notificationKnowsOnlyContracts() {
            DescribedPredicate<JavaClass> outsideAllowed = JavaClass.Predicates.resideInAPackage("com.kobi.territory..")
                .and(DescribedPredicate.not(JavaClass.Predicates.resideInAnyPackage(ROOT + "notification..", ROOT + "common..",
                    ROOT + "catalog.api.query..", ROOT + "progression.api.query..", ROOT + "exploration.api.event..")))
                .as("notification·common·catalog.api.query·progression.api.query·exploration.api.event 밖의 프로젝트 클래스");
            noClasses().that().resideInAPackage(ROOT + "notification..")
                .should().dependOnClassesThat(outsideAllowed)
                .as("notification 은 자기 자신·common·catalog.api.query·progression.api.query·exploration.api.event 만 참조한다")
                .check(PRODUCTION);
        }

        /**
         * sharing(4단계): common, catalog, 모든 컨텍스트의 api.event·api.query 허용(§1). 아무 컨텍스트도 sharing 을 참조하지 않는다(위
         * 규칙들 — 공유는 최하류). 5단계 리더 결정: 공유는 소셜을 직접 참조하지 않는다(소셜이 공유의 공개 범위를 물어야 해서 둘이 서로
         * 참조하면 순환). 공유의 FriendDirectory 포트 ← app-api FriendDirectoryAdapter ← social.api.query.FriendshipQuery.
         */
        @Test
        @DisplayName("공유는 소셜을 모른다 — 친구 관계는 조립 모듈이 이어 준다")
        void sharingDoesNotKnowSocial() {
            forbid("sharing", "social").check(PRODUCTION);
        }
    }

    // ---- 규칙 정의(ArchitectureRuleRegressionTest 가 흉내 낸 패키지에도 같은 규칙을 적용한다) ---------------------------

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
