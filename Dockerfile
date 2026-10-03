# syntax=docker/dockerfile:1.7
# 나의 영토(territory) 운영 이미지 — 멀티스테이지: Gradle bootJar → Spring Boot 레이어 추출 → Java 21 JRE(비루트).
# 테스트는 CI/로컬(./gradlew clean build)에서 이미 돌리므로 여기서는 bootJar 만 만든다(-x test).

########## 1) build ##########
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 캐시 레이어: 빌드 스크립트만 먼저 복사해 의존성을 받아 둔다(소스만 바뀌면 이 레이어는 재사용).
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY common/build.gradle common/
COPY catalog/build.gradle catalog/
COPY exploration/build.gradle exploration/
COPY progression/build.gradle progression/
COPY wardrobe/build.gradle wardrobe/
COPY social/build.gradle social/
COPY sharing/build.gradle sharing/
COPY app-api/build.gradle app-api/
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew --no-daemon -q :app-api:dependencies --configuration runtimeClasspath > /dev/null

# 소스 → bootJar
COPY common ./common
COPY catalog ./catalog
COPY exploration ./exploration
COPY progression ./progression
COPY wardrobe ./wardrobe
COPY social ./social
COPY sharing ./sharing
COPY app-api ./app-api
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :app-api:bootJar -x test \
 && java -Djarmode=tools -jar app-api/build/libs/territory.jar extract --layers --launcher --destination /workspace/extracted

########## 2) runtime ##########
FROM eclipse-temurin:21-jre AS runtime

# fontconfig: headless Java2D(공유 카드 PNG) 글꼴 초기화. 한글 글꼴 자체는 sharing 리소스에 번들(DoHyeon·NanumGothic).
# curl: HEALTHCHECK 용. (베이스 이미지에 이미 있으면 그대로 넘어간다)
RUN apt-get update \
 && apt-get install -y --no-install-recommends fontconfig curl \
 && rm -rf /var/lib/apt/lists/*

# 비루트 사용자 + 카드 저장 디렉터리(네임드 볼륨 첫 마운트 때 이 소유권이 복사된다)
RUN groupadd --system --gid 10001 territory \
 && useradd --system --uid 10001 --gid territory --home-dir /app --shell /usr/sbin/nologin territory \
 && mkdir -p /app /data/share-cards \
 && chown -R territory:territory /app /data

WORKDIR /app
# Spring Boot 레이어(바뀌는 빈도 낮은 순) — 앱 코드만 바뀌면 마지막 레이어만 갈린다.
COPY --from=build --chown=territory:territory /workspace/extracted/dependencies/ ./
COPY --from=build --chown=territory:territory /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=territory:territory /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=territory:territory /workspace/extracted/application/ ./

USER territory

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=25 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true -Dfile.encoding=UTF-8" \
    TERRITORY_CARD_DIR=/data/share-cards

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health > /dev/null || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
