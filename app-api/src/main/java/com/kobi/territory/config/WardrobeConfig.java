package com.kobi.territory.config;

import com.kobi.territory.wardrobe.application.WardrobeSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 꾸미기(3단계) 설정값 → 정책 값 변환. 모듈은 app-api 를 모르므로 record 빈으로 넘긴다.
 * territory.wardrobe.style-points.* = 희귀도별 꾸미기 점수(StylePoints = 착용 아이템 점수 합).
 */
@Configuration
public class WardrobeConfig {

    @Bean
    public WardrobeSettings wardrobeSettings(@Value("${territory.wardrobe.style-points.common}") int common,
                                             @Value("${territory.wardrobe.style-points.rare}") int rare,
                                             @Value("${territory.wardrobe.style-points.legend}") int legend) {
        return new WardrobeSettings(common, rare, legend);
    }
}
