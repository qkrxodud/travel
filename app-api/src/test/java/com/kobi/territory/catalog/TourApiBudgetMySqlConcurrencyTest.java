package com.kobi.territory.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.catalog.infra.client.TourApiCallCounter;
import com.kobi.territory.support.IntegrationTestConfig;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TourAPI 하루 호출 예산에 호출이 몰릴 때 — MySQL(기본 REPEATABLE READ)에서 상한을 넘지 않는다(잠금·격리 규칙의 회귀 테스트). Docker 가 없으면
 * 건너뛴다. 같은 날 첫 호출(행이 아직 없음)이 동시에 오는 경우도 포함한다. V11 마이그레이션·엔티티 검증도 MySQL 에서 함께 확인된다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.h2.console.enabled=false",
    "spring.datasource.hikari.maximum-pool-size=30",
    "territory.tourapi.collect.on-startup=false",
    "territory.tourapi.collect.cron=-"
})
@Import(IntegrationTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("TourAPI 하루 호출 예산에 호출이 몰릴 때")
class TourApiBudgetMySqlConcurrencyTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private static final ExecutorService POOL = Executors.newFixedThreadPool(24);
    private static int day = 0;

    @Autowired TourApiCallCounter counter;
    @Autowired JdbcTemplate jdbc;

    @AfterAll
    static void shutdown() {
        POOL.shutdownNow();
    }

    @RepeatedTest(value = 3, name = "{displayName} — {currentRepetition}/{totalRepetitions}회째")
    @DisplayName("그날 첫 호출 스무 건이 한꺼번에 와도 상한(일곱 번)만큼만 부른다")
    void neverExceedsLimit() throws Exception {
        LocalDate 그날 = LocalDate.of(2027, 2, 1).plusDays(day++);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Callable<Boolean> acquire = () -> {
                start.await();
                return counter.tryAcquire(그날, 7);
            };
            results.add(POOL.submit(acquire));
        }
        start.countDown();
        int allowed = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) allowed++;
        }

        assertThat(allowed).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT calls FROM tourapi_usage WHERE usage_date = ?", Integer.class, 그날)).isEqualTo(7);
        assertThat(counter.usedOn(그날)).isEqualTo(7);
    }
}
