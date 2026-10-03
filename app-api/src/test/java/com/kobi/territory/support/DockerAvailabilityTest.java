package com.kobi.territory.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * 1단계 N1: CI 에서 MySQL 회귀 테스트(Testcontainers, Docker 없으면 skip)가 조용히 빠지지 않게 한다. 환경변수 CI=true(GitHub Actions 등
 * 대부분의 CI 가 설정) 또는 -Dterritory.require-docker=true 이면 Docker 가 반드시 있어야 하고, 없으면 이 테스트가 실패한다.
 * 로컬(둘 다 없음)에서는 아무것도 확인하지 않는다.
 */
@DisplayName("검증 환경")
class DockerAvailabilityTest {

    @Test
    @DisplayName("지속 통합 환경에서는 실제 데이터베이스 동시성 검증이 조용히 건너뛰어지지 않는다")
    void concurrencyChecksAreNotSkippedOnCi() {
        boolean required = "true".equalsIgnoreCase(System.getenv("CI"))
            || Boolean.parseBoolean(System.getProperty("territory.require-docker", "false"));
        if (!required) return;
        assertThat(DockerClientFactory.instance().isDockerAvailable())
            .as("CI 에서 Docker 를 찾지 못했습니다 — MySQL 동시성 회귀 테스트가 skip 됩니다. CI 러너에 Docker 를 준비하세요.")
            .isTrue();
    }
}
