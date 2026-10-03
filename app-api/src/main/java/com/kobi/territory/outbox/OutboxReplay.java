package com.kobi.territory.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.event.DomainEvent;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 읽기 모델 재구성(5단계): outbox 에 쌓인 이벤트(발행 여부와 무관하게 전부)를 id 순으로 처리기에 다시 넘긴다. outbox 행은 지우지 않으므로
 * 투영을 처음부터 다시 만들 수 있다. id 순서는 레인((aggregateId, 구독자)) 순서를 포함하므로 결과가 릴레이로 받은 것과 같다. 전달 기록
 * (outbox_delivery)은 건드리지 않는다.
 * <p>
 * 페이지(200행)마다 트랜잭션 하나. 페이지가 실패하면 그 페이지를 건마다 따로(트랜잭션 분리) 다시 처리하고, 그래도 실패하는 건은
 * 건너뛰고 skipped 로 센다(교체할지는 호출자가 임계값으로 판단 — QA r2 P3-A). 읽을 수 없는 행(예전 타입)은 unreadable 로 따로 센다.
 */
@Component
public class OutboxReplay {

    private static final Logger log = LoggerFactory.getLogger(OutboxReplay.class);
    private static final int PAGE = 200;

    private final OutboxEventRepository events;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public OutboxReplay(OutboxEventRepository events, ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 재생 결과: 넘긴 이벤트 수·처리 실패로 건너뛴 수·읽을 수 없어 건너뛴 수. */
    public record Result(int replayed, int skipped, int unreadable) {}

    /** 진행률(비동기 재구성 상태 조회용) — 재생 중 계속 갱신된다. */
    public static final class Progress {
        private final AtomicInteger replayed = new AtomicInteger();
        private final AtomicInteger skipped = new AtomicInteger();
        private final AtomicInteger unreadable = new AtomicInteger();

        public int replayed() { return replayed.get(); }
        /** 처리기 실패로 건너뛴 수. */
        public int skipped() { return skipped.get(); }
        /** 역직렬화할 수 없어(예전 타입 등) 건너뛴 수. */
        public int unreadable() { return unreadable.get(); }
    }

    public Result replay(Predicate<DomainEvent> accepts, Consumer<DomainEvent> handler, Progress progress) {
        Objects.requireNonNull(progress, "progress");
        long cursor = 0;
        List<OutboxEventEntity> page;
        do {
            page = events.findByIdGreaterThanOrderByIdAsc(cursor, Limit.of(PAGE));
            List<DomainEvent> accepted = page.stream().map(row -> deserialize(row, progress))
                .filter(event -> event != null && accepts.test(event)).toList();
            try {
                tx.executeWithoutResult(status -> accepted.forEach(handler));
                progress.replayed.addAndGet(accepted.size());
            } catch (RuntimeException pageFailure) {
                log.warn("outbox 재생 페이지 실패 — 건마다 다시: {}", pageFailure.toString());
                accepted.forEach(event -> replayOne(event, handler, progress));
            }
            if (!page.isEmpty()) cursor = page.get(page.size() - 1).id();
        } while (page.size() == PAGE);
        log.info("outbox 재생: {}건, 처리 실패 {}건, 읽기 불가 {}건", progress.replayed(), progress.skipped(), progress.unreadable());
        return new Result(progress.replayed(), progress.skipped(), progress.unreadable());
    }

    private void replayOne(DomainEvent event, Consumer<DomainEvent> handler, Progress progress) {
        try {
            tx.executeWithoutResult(status -> handler.accept(event));
            progress.replayed.incrementAndGet();
        } catch (RuntimeException failure) {
            progress.skipped.incrementAndGet();
            log.error("outbox 재생 건너뜀({}): {}", event.getClass().getSimpleName(), failure.toString());
        }
    }

    private DomainEvent deserialize(OutboxEventEntity row, Progress progress) {
        try {
            return (DomainEvent) objectMapper.readValue(row.payload(), Class.forName(row.eventType()));
        } catch (Exception exception) {
            progress.unreadable.incrementAndGet();
            log.warn("outbox {} 재생 건너뜀(역직렬화 실패 {}): {}", row.id(), row.eventType(), exception.toString());
            return null;
        }
    }
}
