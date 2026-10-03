package com.kobi.territory.readmodel;

import com.kobi.territory.outbox.OutboxRelay;
import com.kobi.territory.outbox.OutboxReplay;
import com.kobi.territory.social.application.FeedRebuildService;
import com.kobi.territory.social.application.SocialSubscriptions;
import com.kobi.territory.social.domain.feed.FeedGeneration;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 친구 소식 재구성 작업(5단계 리더 결정 4 — 운영 POST /admin/rebuild/feed, local POST /dev/rebuild/feed). QA P3-2 의 문제를 이렇게 푼다:
 * <ul>
 *   <li><b>릴레이 전체를 멈추지 않는다</b> — 릴레이는 {@code social.feed} 구독자 몫만 대기로 남기고(구독자 단위 정지) 진행·꾸미기 등은 계속
 *       전달한다. 재구성이 끝나면 대기분이 새 세대로 이어 전달된다(투영 멱등 — 재생과 겹쳐도 같다).</li>
 *   <li><b>비동기</b> — 시작은 바로 돌아오고(작업 스레드 하나, 동시에 하나만) 상태({@link FeedRebuildStatus})를 조회한다.</li>
 *   <li><b>이전 피드 보존</b> — 다음 세대에 처음부터 쌓고 다 쌓였을 때만 지금 세대를 바꾼다. 실패하면 쌓던 세대만 지우고 지금 피드는 그대로,
 *       재구성 중에도 피드는 비지 않는다(지금 세대가 그대로 읽힌다). 처리 실패 건은 건너뛰고 세되, territory.social.feed-rebuild.max-skipped
 *       (기본 0)를 넘으면 FAILED 로 이전 세대를 유지한다(QA r2 P3-A). 읽을 수 없는 예전 행은 따로 세고 교체를 막지 않는다.</li>
 * </ul>
 * 순서: 구독자 정지(진행 중인 릴레이 주기가 끝날 때까지 기다림) → 다음 세대 비우기 → outbox 처음부터 끝까지 재생 → 세대 교체 → 구독자 재개.
 * 정지 전에 옛 세대로 전달된 이벤트도 outbox 에 있으므로 재생에 포함된다. 단일 인스턴스 가정(다른 인스턴스의 릴레이는 멈추지 못한다).
 */
@Component
public class FeedRebuildJob implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(FeedRebuildJob.class);

    private final FeedRebuildService rebuild;
    private final OutboxReplay replay;
    private final ObjectProvider<OutboxRelay> relay;
    private final Clock clock;
    private final int maxSkipped;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "feed-rebuild");
        thread.setDaemon(true);
        return thread;
    });
    private volatile FeedRebuildStatus status = FeedRebuildStatus.idle();
    private volatile OutboxReplay.Progress progress;

    public FeedRebuildJob(FeedRebuildService rebuild, OutboxReplay replay, ObjectProvider<OutboxRelay> relay, Clock clock,
                          @Value("${territory.social.feed-rebuild.max-skipped:0}") int maxSkipped) {
        this.maxSkipped = maxSkipped;
        this.rebuild = rebuild;
        this.replay = replay;
        this.relay = relay;
        this.clock = clock;
    }

    /** 비동기 시작. 이미 돌고 있으면 시작하지 않고 그 상태를 낸다(started=false). */
    public synchronized Started start() {
        if (status.running()) return new Started(false, current(), null);
        progress = new OutboxReplay.Progress();
        status = FeedRebuildStatus.running(rebuild.live().next().value(), clock.instant());
        Future<?> future = worker.submit(this::run);
        return new Started(true, current(), future);
    }

    /** 지금 상태(진행 중이면 재생 건수 포함). */
    public FeedRebuildStatus current() {
        OutboxReplay.Progress running = progress;
        return status.running() && running != null
            ? status.withProgress(running.replayed(), running.skipped(), running.unreadable()) : status;
    }

    private void run() {
        OutboxReplay.Progress tracking = progress;
        Instant startedAt = status.startedAt();
        FeedGeneration building = null;
        relay.ifAvailable(active -> active.pauseSubscriber(SocialSubscriptions.FEED_SUBSCRIBER));
        try {
            building = rebuild.begin();
            FeedGeneration target = building;
            OutboxReplay.Result result = replay.replay(rebuild::accepts, event -> rebuild.project(event, target), tracking);
            if (result.skipped() > maxSkipped) {
                // 처리 실패가 임계(기본 0)를 넘으면 구멍 난 세대로 바꾸지 않는다 — 이전 세대 유지(QA r2 P3-A)
                throw new IllegalStateException("처리 실패 " + result.skipped() + "건 > 허용 " + maxSkipped
                    + "(territory.social.feed-rebuild.max-skipped) — 로그의 'outbox 재생 건너뜀' 확인");
            }
            int removed = rebuild.complete(building);
            status = FeedRebuildStatus.succeeded(building.value(), result.replayed(), result.skipped(), result.unreadable(), startedAt,
                clock.instant());
            log.info("친구 소식 재구성 완료: 세대 {} — 재생 {}건, 처리 실패 {}건, 읽기 불가 {}건, 옛 세대 {}행 정리", building.value(),
                result.replayed(), result.skipped(), result.unreadable(), removed);
        } catch (RuntimeException failure) {
            if (building != null) abandonQuietly(building);
            status = FeedRebuildStatus.failed(building == null ? 0 : building.value(), tracking.replayed(), tracking.skipped(),
                tracking.unreadable(), startedAt, clock.instant(), failure.toString());
            log.error("친구 소식 재구성 실패 — 이전 세대를 그대로 둔다", failure);
        } finally {
            relay.ifAvailable(active -> active.resumeSubscriber(SocialSubscriptions.FEED_SUBSCRIBER));
        }
    }

    private void abandonQuietly(FeedGeneration building) {
        try {
            rebuild.abandon(building);
        } catch (RuntimeException cleanupFailure) {
            log.warn("실패한 세대 {} 정리 실패(다음 재구성 시작 때 지운다): {}", building.value(), cleanupFailure.toString());
        }
    }

    @Override
    public void destroy() {
        worker.shutdownNow();
    }

    /** @param future 시작했으면 끝날 때까지 기다릴 수 있는 핸들(local 동기 실행용) */
    public record Started(boolean started, FeedRebuildStatus status, Future<?> future) {}
}
