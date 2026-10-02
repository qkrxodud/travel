package com.kobi.territory.support;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;

/**
 * 통합 테스트 공통 설정: 가변 시계(@Primary, 2026-10-02 12:00 KST 시작) + outbox 릴레이가 발행한 이벤트 수집기.
 */
@TestConfiguration
public class IntegrationTestConfig {

    public static final Instant START = Instant.parse("2026-10-02T03:00:00Z");

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(START, ZoneId.of("Asia/Seoul"));
    }

    @Bean
    public CapturedEvents capturedEvents() {
        return new CapturedEvents();
    }

    public static class CapturedEvents {
        private final List<DomainEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void on(DomainEvent event) {
            events.add(event);
        }

        public List<DomainEvent> all() {
            return List.copyOf(events);
        }
    }
}
