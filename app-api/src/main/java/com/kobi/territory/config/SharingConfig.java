package com.kobi.territory.config;

import com.kobi.territory.sharing.application.SharingSettings;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 공유(4단계) 설정값 → 정책 값 변환. 모듈은 app-api 를 모르므로 record 빈으로 넘긴다.
 * territory.share-card.cache-ttl-minutes = 카드 최소 캐시 시간(원천이 바뀌어도 그동안은 그린 카드를 낸다).
 * territory.public-base-url = 공개 기준 주소(og:image 절대 주소 — 요청 Host 를 믿지 않는다).
 * 카드 이미지 저장 경로 territory.share-card.storage-dir 는 저장소 어댑터가 직접 받는다(인프라 설정).
 */
@Configuration
public class SharingConfig {

    @Bean
    public SharingSettings sharingSettings(TerritoryProperties props,
                                           @Value("${territory.public-base-url}") String publicBaseUrl) {
        return new SharingSettings(Duration.ofMinutes(props.shareCard().cacheTtlMinutes()), publicBaseUrl);
    }
}
