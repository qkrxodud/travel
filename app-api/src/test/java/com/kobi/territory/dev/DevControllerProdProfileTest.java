package com.kobi.territory.dev;

import static org.assertj.core.api.Assertions.assertThat;

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
    "TERRITORY_ADMIN_TOKEN=prod-profile-test-token"
})
@ActiveProfiles("prod")
class DevControllerProdProfileTest {

    @Autowired ApplicationContext context;

    @Test
    void prod_프로파일에서는_DevController_빈이_없다() {
        assertThat(context.getBeansOfType(DevController.class)).isEmpty();
        assertThat(context.containsBean("devController")).isFalse();
    }
}
