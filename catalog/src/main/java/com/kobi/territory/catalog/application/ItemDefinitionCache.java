package com.kobi.territory.catalog.application;

import com.kobi.territory.catalog.domain.item.ItemDefinitionRepository;
import com.kobi.territory.catalog.domain.item.ItemDefinitions;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 아이템 정의 짧은 캐시(QA P3-15) — 이벤트·장면·가방 처리마다 item_definition 전체를 읽지 않게.
 * <p>
 * 무효화(운영 추가·dev 초기화)는 <b>커밋 뒤에</b> 세대 번호를 올린다. 조회는 읽기 전에 세대를 기억했다가, 읽는 동안 세대가 바뀌었으면
 * (= 커밋 전에 시작한 읽기가 옛 내용을 들고 왔을 수 있음) 그 결과를 캐시에 넣지 않는다(QA P3-R2-6 — 무효화 직후 옛 스냅샷이 다시
 * 자리 잡는 창을 닫는다). 남는 한계: 다른 인스턴스에서 추가한 정의는 최대 {@link #TTL} 뒤에 보인다 — 운영 추가 아이템은 다음
 * 체크인부터 받는 이슈 아이템이라 30초 지연은 허용한다(소급 지급 없음 규칙과 함께, 그 사이 체크인은 받지 않을 수 있다).
 */
@Component
public class ItemDefinitionCache {

    static final Duration TTL = Duration.ofSeconds(30);

    private final ItemDefinitionRepository repository;
    private final AtomicLong generation = new AtomicLong();
    private volatile Snapshot snapshot;

    public ItemDefinitionCache(ItemDefinitionRepository repository) {
        this.repository = repository;
    }

    public ItemDefinitions current() {
        Snapshot cached = snapshot;
        long now = System.nanoTime();
        long seen = generation.get();
        if (cached != null && cached.generation() == seen && now - cached.loadedAtNanos() <= TTL.toNanos()) {
            return cached.items();
        }
        ItemDefinitions loaded = repository.loadAll();
        if (generation.get() == seen) snapshot = new Snapshot(loaded, now, seen);
        return loaded;
    }

    /** 정의가 바뀌었다 — 트랜잭션 안이면 커밋 뒤에, 아니면 바로 세대를 올린다. */
    public void invalidate() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    generation.incrementAndGet();
                }
            });
            return;
        }
        generation.incrementAndGet();
    }

    private record Snapshot(ItemDefinitions items, long loadedAtNanos, long generation) {}
}
