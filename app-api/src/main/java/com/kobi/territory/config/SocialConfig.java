package com.kobi.territory.config;

import com.kobi.territory.social.application.SocialSettings;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 소셜(5단계) 설정값 → 정책 값. territory.social.feed-size = 친구 소식 건수, territory.social.stats-cache-ttl = 상위 %·지역 통계
 * 애플리케이션 캐시 시간(Redis 도입 전). 배치 주기 territory.social.rank-batch-cron 은 배치 빈이 직접 받는다(스케줄 설정).
 */
@Configuration
public class SocialConfig {

    @Bean
    public SocialSettings socialSettings(@Value("${territory.social.feed-size:30}") int feedSize,
                                         @Value("${territory.social.stats-cache-ttl:5m}") Duration statsCacheTtl) {
        return new SocialSettings(feedSize, statsCacheTtl);
    }
}
