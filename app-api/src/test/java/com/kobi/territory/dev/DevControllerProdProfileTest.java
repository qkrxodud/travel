package com.kobi.territory.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.catalog.api.query.MysteryRegionQuery;
import com.kobi.territory.catalog.application.MysteryService;
import com.kobi.territory.analytics.application.AnalyticsSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * prod 프로파일에서는 DevController 빈이 없어야 한다.
 * prod 데이터소스 환경변수는 테스트용 H2 값으로 대체한다(엔티티가 없으므로 validate 통과).
 */
@SpringBootTest(properties = {
    "DB_URL=jdbc:h2:mem:prodprofiletest;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "DB_USERNAME=sa",
    "DB_PASSWORD=",
    "TERRITORY_ADMIN_TOKEN=prod-profile-test-token",
    "TERRITORY_PUBLIC_BASE_URL=https://territory.example",
    "TERRITORY_MYSTERY_SALT=prod-profile-test-salt",
    "TERRITORY_ANALYTICS_SALT=prod-profile-analytics-salt"
})
@ActiveProfiles("prod")
@DisplayName("운영 환경")
class DevControllerProdProfileTest {

    @Autowired ApplicationContext context;

    @Test
    @DisplayName("개발 도구가 없고 켤 설정도 없다")
    void noDevTools() {
        assertThat(context.getBeansOfType(DevController.class)).isEmpty();
        assertThat(context.containsBean("devController")).isFalse();
        assertThat(context.getEnvironment().getProperty("territory.dev.enabled")).isNull(); // local 파일에만 있다
    }

    @Test
    @DisplayName("미스터리 지역을 고정하는 개발용 어댑터가 없어 원래 주차 선택 그대로이고, 시드 비밀값은 환경변수에서 온다")
    void noMysteryPin() {
        assertThat(context.getBeansOfType(PinnableMysteryRegionQuery.class)).isEmpty();
        assertThat(context.getBean(MysteryRegionQuery.class)).isInstanceOf(MysteryService.class);
        assertThat(context.getEnvironment().getProperty("territory.mystery.salt")).isEqualTo("prod-profile-test-salt");
    }

    @Test
    @DisplayName("분석 시드 도구가 없고, 탐험가 해시의 비밀값은 환경변수에서 온다")
    void analyticsSaltFromEnvironment() {
        assertThat(context.getBeansOfType(AnalyticsDevController.class)).isEmpty();
        assertThat(context.getBean(AnalyticsSettings.class).salt()).isEqualTo("prod-profile-analytics-salt");
        assertThat(context.getBean(AnalyticsSettings.class).liveCacheTtl()).isPositive();
    }
}
