# 뼈대 파일 전문

셋업 가이드 PDF의 "Gradle 설정", "application.yml", "최소 코드" 섹션을 그대로 옮긴 것이다. 수정 없이 이대로 생성한다.

## settings.gradle

```groovy
pluginManagement {
    plugins {
        id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
    }
}
plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}

rootProject.name = 'territory'

include 'common'
include 'catalog'
include 'exploration'
include 'progression'
include 'wardrobe'
include 'social'
include 'sharing'
include 'app-api'
```

(foojay resolver는 Java 21 toolchain 자동 다운로드용. 로컬에 JDK 21이 있으면 없어도 되지만 두는 편이 안전하다.)

## gradle.properties

```properties
org.gradle.jvmargs=-Xmx2g
org.gradle.parallel=true
org.gradle.caching=true
```

## 루트 build.gradle

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.6' apply false
    id 'io.spring.dependency-management' version '1.1.7' apply false
}

allprojects {
    group = 'com.kobi'
    version = '0.0.1-SNAPSHOT'
    repositories { mavenCentral() }
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'java-library'
    apply plugin: 'io.spring.dependency-management'

    java {
        toolchain { languageVersion = JavaLanguageVersion.of(21) }
    }

    dependencyManagement {
        imports {
            mavenBom org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES
        }
    }

    dependencies {
        compileOnly 'org.projectlombok:lombok'
        annotationProcessor 'org.projectlombok:lombok'
        testImplementation 'org.springframework.boot:spring-boot-starter-test'
    }

    tasks.named('test') { useJUnitPlatform() }
}
```

## common/build.gradle

```groovy
dependencies {
    implementation 'org.springframework:spring-context'
}
```

(이벤트 퍼블리셔 인터페이스용 spring-context만. starter-web·jpa는 넣지 않는다.)

## 도메인 모듈 build.gradle (exploration 예시 — catalog, progression, wardrobe, social, sharing 동일 패턴)

```groovy
dependencies {
    api project(':common')
    implementation project(':catalog')    // exploration, wardrobe에만 넣는다

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
}
```

## app-api/build.gradle

```groovy
plugins {
    id 'org.springframework.boot'
}

dependencies {
    implementation project(':common')
    implementation project(':catalog')
    implementation project(':exploration')
    implementation project(':progression')
    implementation project(':wardrobe')
    implementation project(':social')
    implementation project(':sharing')

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    runtimeOnly 'com.h2database:h2'
    runtimeOnly 'com.mysql:mysql-connector-j'

    testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
}

bootJar { archiveFileName = 'territory.jar' }
```

## app-api/src/main/resources/application.yml

```yaml
spring:
  application:
    name: territory
  profiles:
    default: local
  jpa:
    open-in-view: false
    properties:
      hibernate:
        default_batch_fetch_size: 100
  jackson:
    time-zone: Asia/Seoul

server:
  port: 8080
  shutdown: graceful

management:
  endpoints:
    web:
      exposure:
        include: health, info

territory:
  check-in:
    daily-cap: 5
    onboarding-grace-hours: 72
  map:
    leave-grace-days: 7
  share-card:
    cache-ttl-minutes: 10
```

## app-api/src/main/resources/application-local.yml

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:territory;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  h2:
    console:
      enabled: true
      path: /h2-console

logging:
  level:
    com.kobi.territory: DEBUG
```

## app-api/src/main/resources/application-prod.yml

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

## app-api — src/main/java/com/kobi/territory/TerritoryApplication.java

```java
package com.kobi.territory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TerritoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(TerritoryApplication.class, args);
    }
}
```

패키지가 `com.kobi.territory`라 하위 모듈의 빈이 전부 스캔된다. 모듈마다 `@Configuration`을 따로 만들 필요가 없다.

## app-api — src/main/java/com/kobi/territory/config/TerritoryProperties.java

```java
package com.kobi.territory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "territory")
public record TerritoryProperties(CheckIn checkIn, Map map, ShareCard shareCard) {
    public record CheckIn(int dailyCap, int onboardingGraceHours) {}
    public record Map(int leaveGraceDays) {}
    public record ShareCard(int cacheTtlMinutes) {}
}
```

## app-api — src/main/java/com/kobi/territory/api/HealthController.java

```java
package com.kobi.territory.api;

import com.kobi.territory.config.TerritoryProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final TerritoryProperties props;

    public HealthController(TerritoryProperties props) { this.props = props; }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "territory",
            "dailyCap", props.checkIn().dailyCap(),
            "leaveGraceDays", props.map().leaveGraceDays()
        );
    }
}
```

## common — src/main/java/com/kobi/territory/common/event/DomainEvent.java

```java
package com.kobi.territory.common.event;

import java.time.Instant;

public interface DomainEvent {
    Instant occurredAt();
}
```

## app-api — src/test/java/com/kobi/territory/ArchitectureTest.java

```java
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
                                "jakarta.persistence..");

    @ArchTest
    static final ArchRule exploration_talks_only_through_common =
        noClasses().that().resideInAPackage("com.kobi.territory.exploration..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.kobi.territory.progression..",
                                "com.kobi.territory.wardrobe..",
                                "com.kobi.territory.social..",
                                "com.kobi.territory.sharing..");

    // 같은 형태의 규칙을 progression, wardrobe, social, sharing, catalog에도 복제한다.
    // 각 규칙의 금지 목록은 "자신과 catalog를 제외한 나머지 도메인 모듈 전부"다
    // (catalog는 exploration·wardrobe가 읽기 전용으로 참조할 수 있으므로 금지 목록에서 뺀다).
    //
    // 주의: 이 전면 금지 규칙은 도메인 코드가 없는 뼈대 단계용이다. 1단계부터 컨텍스트 간
    // 이벤트 구독이 생기면 implement-context 스킬의 "의존 규칙의 진화"에 따라
    // "..{context}.api.. 를 제외한 타 컨텍스트 참조 금지" 형태로 바꾸고,
    // 모듈 build.gradle에도 허용된 project 의존(예: progression → exploration)을 추가한다.
}
```

`domain` 패키지에서 `jakarta.persistence`를 막은 것은 JPA 엔티티를 `infra`에 두고 도메인 객체와 분리하겠다는 선언이다.

## 도메인 모듈 패키지 구조 (6개 모듈 공통)

```
<module>/src/main/java/com/kobi/territory/<module>/
├─ api/package-info.java           # Controller, Request/Response DTO, 공개 이벤트·Query
├─ application/package-info.java   # UseCase 서비스, 트랜잭션 경계, 이벤트 발행
├─ domain/package-info.java        # 애그리거트, VO, 도메인 이벤트, Repository 인터페이스
└─ infra/package-info.java         # JPA 엔티티/구현체, 외부 클라이언트
```
