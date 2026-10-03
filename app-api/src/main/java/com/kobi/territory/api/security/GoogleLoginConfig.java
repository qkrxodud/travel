package com.kobi.territory.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * 구글 OIDC 클라이언트 등록 — 환경변수 GOOGLE_CLIENT_ID·GOOGLE_CLIENT_SECRET(설정 키 territory.auth.google.*)이 <b>둘 다 있을 때만</b>.
 * 없으면 이 빈이 없고 SecurityConfig 가 oauth2Login 을 켜지 않는다 — 앱은 정상 기동하고 로그인 버튼만 비활성(GET /auth/session 의
 * googleLoginEnabled=false). 값은 코드·yml·로그에 두지 않는다(yml 에는 환경변수 참조만).
 * redirect_uri 는 신뢰된 설정 territory.public-base-url 로 만든다(QA P3-2 — 요청 Host 헤더를 믿지 않음). 비어 있으면(local 만) 스프링 기본
 * {@code {baseUrl}} 를 쓴다.
 */
@Configuration
public class GoogleLoginConfig {

    public static final String REGISTRATION_ID = "google";

    @Bean
    @Conditional(GoogleConfigured.class)
    ClientRegistrationRepository clientRegistrationRepository(@Value("${territory.auth.google.client-id}") String clientId,
                                                              @Value("${territory.auth.google.client-secret}") String secret,
                                                              PublicBaseUrl publicBaseUrl) {
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder(REGISTRATION_ID)
            .clientId(clientId.strip()).clientSecret(secret.strip())
            .redirectUri(publicBaseUrl.redirectUriTemplate()).build());
    }

    /** 클라이언트 ID·시크릿이 둘 다 비어 있지 않은지. */
    static final class GoogleConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return configured(context.getEnvironment().getProperty("territory.auth.google.client-id"),
                context.getEnvironment().getProperty("territory.auth.google.client-secret"));
        }
    }

    static boolean configured(String clientId, String secret) {
        return clientId != null && !clientId.isBlank() && secret != null && !secret.isBlank();
    }
}
