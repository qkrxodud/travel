package com.kobi.territory.support;

import com.kobi.territory.common.event.DomainEvent;
import com.kobi.territory.common.event.EventSubscriber;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.progression.domain.collectionbook.CollectionBook;
import com.kobi.territory.progression.domain.collectionbook.CollectionBookRepository;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;

/**
 * 통합 테스트 공통 설정.
 * <ul>
 *   <li>가변 시계(@Primary, 2026-10-02 12:00 KST 시작)</li>
 *   <li>outbox 릴레이가 발행한 이벤트 수집기</li>
 *   <li>항상 실패하는 구독자 {@link PoisonSubscriber}({@code POISONED}에 넣은 탐험가의 RegionVisited 만 실패)</li>
 *   <li>장애 주입 {@link FaultInjection}: 실제 진행 구독자를 감싸 지정한 탐험가·이벤트 타입 처리를 n번 실패시킨다</li>
 * </ul>
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

    @Bean
    public PoisonSubscriber poisonSubscriber() {
        return new PoisonSubscriber();
    }

    @Bean
    public static BeanPostProcessor faultInjectingSubscribers() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof EventSubscriber subscriber && subscriber.id().startsWith("progression.")
                    ? FaultInjection.wrap(subscriber) : bean;
            }
        };
    }

    @Bean
    public static BeanPostProcessor pausableCollectionBooks() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof CollectionBookRepository repository ? ReplacePause.wrap(repository) : bean;
            }
        };
    }

    /**
     * 재계산 일시정지(구조 QA S2-1 재현): arm 후 첫 CollectionBookRepository.replace 호출 직전에 멈춰, 그 사이에 다른
     * 처리(도감 이벤트 반영)를 커밋시킬 수 있게 한다. 한 번만 멈춘다(재계산 재시도는 그대로 통과).
     */
    public static final class ReplacePause {
        private static volatile CountDownLatch reached;
        private static volatile CountDownLatch release;

        private ReplacePause() {}

        public static void arm() {
            reached = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        public static boolean awaitReached(Duration timeout) throws InterruptedException {
            return reached.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        public static void release() {
            CountDownLatch waiting = release;
            if (waiting != null) waiting.countDown();
        }

        static CollectionBookRepository wrap(CollectionBookRepository delegate) {
            return new CollectionBookRepository() {
                @Override public CollectionBook load(String mapId) { return delegate.load(mapId); }
                @Override public void save(CollectionBook collectionBook) { delegate.save(collectionBook); }

                @Override
                public void replace(CollectionBook collectionBook) {
                    CountDownLatch waitFor = release;
                    if (waitFor != null && reached.getCount() > 0) {
                        reached.countDown();
                        try {
                            waitFor.await(60, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        release = null;
                    }
                    delegate.replace(collectionBook);
                }
            };
        }
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

    public static class PoisonSubscriber implements EventSubscriber {
        public static final String ID = "test.poison";
        public static final Set<String> POISONED = ConcurrentHashMap.newKeySet();

        @Override public String id() { return ID; }
        @Override public Set<Class<? extends DomainEvent>> eventTypes() { return Set.of(RegionVisited.class); }

        @Override
        public void handle(DomainEvent event) {
            RegionVisited visited = (RegionVisited) event;
            if (POISONED.contains(visited.explorerId())) {
                throw new IllegalStateException("독성 이벤트(테스트): " + visited.regionCode());
            }
        }
    }

    /** 진행 구독자 장애 주입. 지정한 (구독자, 이벤트 타입, 탐험가) 처리를 남은 횟수만큼 실패시킨다. */
    public static final class FaultInjection {

        private record Fault(String subscriberId, Class<?> eventType, String explorerId, AtomicInteger remaining) {}

        private static final List<Fault> FAULTS = new CopyOnWriteArrayList<>();

        private FaultInjection() {}

        public static void failNext(String subscriberId, Class<? extends DomainEvent> eventType, String explorerId,
                                    int times) {
            FAULTS.add(new Fault(subscriberId, eventType, explorerId, new AtomicInteger(times)));
        }

        public static void clear() {
            FAULTS.clear();
        }

        static EventSubscriber wrap(EventSubscriber delegate) {
            return new EventSubscriber() {
                @Override public String id() { return delegate.id(); }
                @Override public Set<Class<? extends DomainEvent>> eventTypes() { return delegate.eventTypes(); }
                @Override public String toString() { return delegate.toString(); }

                @Override
                public void handle(DomainEvent event) {
                    String explorerId = explorerIdOf(event);
                    for (Fault fault : FAULTS) {
                        if (fault.subscriberId().equals(delegate.id()) && fault.eventType().isInstance(event)
                            && fault.explorerId().equals(explorerId) && fault.remaining().getAndDecrement() > 0) {
                            throw new IllegalStateException("주입한 장애(테스트): " + delegate.id() + " " + event);
                        }
                    }
                    delegate.handle(event);
                }
            };
        }

        private static String explorerIdOf(DomainEvent event) {
            try {
                Method accessor = event.getClass().getMethod("explorerId");
                return String.valueOf(accessor.invoke(event));
            } catch (ReflectiveOperationException missing) {
                return "";
            }
        }
    }
}
