package com.kobi.territory.social.application;

import java.time.Duration;
import java.util.Objects;

/**
 * 소셜 설정값(app-api 가 territory.social.* 를 바인딩해 넘긴다 — 모듈은 app-api 를 모른다).
 *
 * @param feedSize       친구 소식 한 번에 보여 줄 건수(territory.social.feed-size)
 * @param statsCacheTtl  상위 %·지역 통계 애플리케이션 캐시 시간(territory.social.stats-cache-ttl — Redis 도입 전, 배치가 끝나면 비운다)
 */
public record SocialSettings(int feedSize, Duration statsCacheTtl) {
    public SocialSettings {
        if (feedSize < 1) throw new IllegalArgumentException("feedSize=" + feedSize);
        Objects.requireNonNull(statsCacheTtl, "statsCacheTtl");
    }
}
