---
name: scaffold-multimodule
description: 나의 영토(territory) Spring Boot 멀티모듈 뼈대를 생성·재생성한다. "뼈대 만들어줘", "프로젝트 셋업", "스캐폴드", "멀티모듈 구조 잡아줘", "모듈 추가/구조 수정" 요청이나 territory-orchestrator의 0단계 실행 시 반드시 이 스킬을 사용할 것. 도메인 코드 구현에는 사용하지 않는다(그건 implement-context).
---

# Scaffold Multimodule — territory 멀티모듈 뼈대 생성

`doc/나의 영토 Spring Boot 프로젝트 셋업 가이드.pdf`를 실행 가능한 절차로 옮긴 스킬이다. 완료 기준은 `./gradlew :app-api:bootRun` 한 줄로 `http://localhost:8080/health`가 뜨는 것이다. 도메인 코드는 넣지 않는다.

## 제약 (전부 강제)

- Java 21, Kotlin 금지. Gradle Groovy DSL(`build.gradle`), Wrapper 포함.
- Spring Boot 3.5.6. 설정은 `application.yml`만(properties 파일 금지). 프로파일 `local`(기본, H2 인메모리) / `prod`(환경변수 주입).
- 루트 프로젝트 `territory`, 그룹 `com.kobi`, 기본 패키지 `com.kobi.territory`, 포트 8080.
- 모듈 8개: `common, catalog, exploration, progression, wardrobe, social, sharing, app-api`.
- `org.springframework.boot` 플러그인은 루트에 `apply false`, app-api에서만 적용(bootJar 이름 `territory.jar`). 나머지 모듈은 `java-library`로 BOM만 공유. 이유: 도메인 모듈에 bootJar 태스크가 생기면 안 된다.
- 의존 방향(뼈대 시점): 모든 도메인 모듈 → common. exploration·wardrobe → catalog(읽기 전용 예외). app-api → 전부. 그 외 도메인 모듈 간 참조 금지. **이 전면 금지는 도메인 코드가 없는 뼈대 단계의 초기 규칙이다** — 1단계부터는 implement-context 스킬의 "의존 규칙의 진화"에 따라 Gradle 의존과 ArchUnit 규칙을 api-예외 형태로 함께 갱신한다.
- 각 도메인 모듈은 `api / application / domain / infra` 네 패키지 고정, 빈 패키지에도 `package-info.java`를 둔다. 이유: 구조가 처음부터 보이게.

## 절차

1. **기존 Initializr 산출물 교체 확인.** 이 레포는 `com.territory.travel` 단일 모듈(Boot 4.1.1)로 생성되어 있다. 다음을 제거/대체한다: `src/`(루트의 것), 루트 `build.gradle`·`settings.gradle`, `HELP.md`. 보존한다: `doc/`, `.claude/`, `CLAUDE.md`, `.gitignore`, `.gitattributes`, gradle wrapper. 제거 전에 대상 파일 내용을 확인해 도메인 코드가 섞여 있지 않은지 본다.
2. **Gradle 호환 확인.** `./gradlew --version`으로 wrapper 버전 확인. Boot 3.5.6 플러그인은 Gradle 8.x 기준이므로 9.x면 `./gradlew wrapper --gradle-version 8.14`로 내린다. `settings.gradle`에 foojay toolchain resolver 플러그인을 유지/추가해 Java 21 toolchain이 자동 프로비저닝되게 한다.
3. **파일 생성.** `references/setup-files.md`의 내용을 그대로 사용한다 — settings.gradle, gradle.properties, 루트/모듈별 build.gradle, application.yml 3종, Java 파일 5개(TerritoryApplication, TerritoryProperties, HealthController, DomainEvent, ArchitectureTest). ArchitectureTest의 모듈 간 참조 금지 규칙은 도메인 모듈 6개 전부에 복제한다(catalog는 금지 목록에서 제외).
4. **검증.** 아래 체크리스트 전부 통과해야 완료다.

## 검증 체크리스트

- [ ] `./gradlew projects` 출력에 모듈 8개
- [ ] `./gradlew clean build` 성공, `app-api/build/libs/territory.jar`만 생성(다른 모듈은 일반 jar)
- [ ] `./gradlew :app-api:bootRun` 후 `curl localhost:8080/health` → `{"status":"UP","dailyCap":5,"leaveGraceDays":7,...}`
- [ ] `curl localhost:8080/actuator/health` → `{"status":"UP"}`
- [ ] prod 프로파일 실행은 환경변수 없으면 실패해야 정상
- [ ] ArchitectureTest 통과. 일부러 exploration에서 progression 클래스를 import하면 실패하는 것 확인 후 원복

## 완료 후

- `CLAUDE.md`의 프로젝트 개요·커맨드 섹션을 멀티모듈 구조에 맞게 갱신한다(하네스 섹션은 건드리지 않는다).
- 자주 걸리는 것: TerritoryProperties 바인딩 실패 → `@ConfigurationPropertiesScan` 누락 또는 yml 키가 kebab-case(`daily-cap`)가 아님. 도메인 모듈에 bootJar가 생김 → 루트 `apply false` 누락.
