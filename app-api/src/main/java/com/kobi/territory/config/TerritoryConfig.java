package com.kobi.territory.config;

import com.kobi.territory.catalog.application.MysterySettings;
import com.kobi.territory.exploration.application.AccountSettings;
import com.kobi.territory.exploration.application.ExplorationSettings;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 설정값 → 각 컨텍스트 정책 값 변환. 모듈은 app-api(TerritoryProperties)를 모르므로 여기서 record 빈으로 넘긴다.
 */
@Configuration
@EnableScheduling
public class TerritoryConfig {

    /**
     * 서버 시계. "하루"·"오늘"의 기준 시간대를 가진다. 테스트는 @Primary 가변 Clock으로 대체한다.
     * 8단계: 앞으로만 밀 수 있는 시계(AdjustableClock) — 미는 길은 local 전용 /dev/clock 뿐(운영은 늘 시스템 시계).
     */
    @Bean
    public Clock clock(@Value("${territory.time-zone:Asia/Seoul}") String zone) {
        return new AdjustableClock(Clock.system(ZoneId.of(zone)));
    }

    @Bean
    public ExplorationSettings explorationSettings(TerritoryProperties props) {
        return new ExplorationSettings(props.checkIn().dailyCap(), props.checkIn().onboardingGraceHours(),
            props.map().leaveGraceDays());
    }

    /**
     * 이번 주 미스터리 지역 설정(8단계 QA Q1): 주차 시드에 섞는 서버 비밀값. local 은 application-local.yml 기본값, 운영은 환경변수
     * TERRITORY_MYSTERY_SALT 필수(application-prod.yml — 없으면 기동 실패). 비밀값을 모르면 다음 주 지역을 미리 셀 수 없다.
     */
    @Bean
    public MysterySettings mysterySettings(@Value("${territory.mystery.salt}") String salt) {
        return new MysterySettings(salt);
    }

    /** 계정 규칙 값(4단계): 바꾸기 전 handle 예약 기간. */
    @Bean
    public AccountSettings accountSettings(@Value("${territory.account.handle-reservation-days:30}") int handleReservationDays) {
        return new AccountSettings(handleReservationDays);
    }
}
