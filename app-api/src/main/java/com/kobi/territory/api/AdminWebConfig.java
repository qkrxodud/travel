package com.kobi.territory.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 운영 API(/admin/**) 보호 등록. 토큰은 설정값 territory.admin.token — local 은 application-local.yml 기본값,
 * prod 는 환경변수 TERRITORY_ADMIN_TOKEN(없으면 기동 실패).
 */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final String adminToken;

    public AdminWebConfig(@Value("${territory.admin.token}") String adminToken) {
        this.adminToken = adminToken;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminTokenInterceptor(adminToken)).addPathPatterns("/admin/**");
    }
}
