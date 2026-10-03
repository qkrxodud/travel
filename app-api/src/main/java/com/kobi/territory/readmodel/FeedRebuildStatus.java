package com.kobi.territory.readmodel;

import java.time.Instant;

/**
 * 친구 소식 재구성 상태(GET /admin/rebuild/feed).
 *
 * @param state      IDLE · RUNNING · SUCCEEDED · FAILED
 * @param generation 쌓는(쌓은) 세대
 * @param replayed   지금까지 재생한 이벤트 수
 * @param skipped    처리 실패로 건너뛴 이벤트 수(territory.social.feed-rebuild.max-skipped 를 넘으면 FAILED — 이전 세대 유지)
 * @param unreadable 읽을 수 없어 건너뛴 outbox 행 수(예전 타입 — 교체를 막지 않는다)
 * @param error      실패 원인(FAILED 일 때만)
 */
public record FeedRebuildStatus(String state, int generation, int replayed, int skipped, int unreadable, Instant startedAt,
                                Instant finishedAt, String error) {

    static FeedRebuildStatus idle() {
        return new FeedRebuildStatus("IDLE", 0, 0, 0, 0, null, null, null);
    }

    static FeedRebuildStatus running(int generation, Instant startedAt) {
        return new FeedRebuildStatus("RUNNING", generation, 0, 0, 0, startedAt, null, null);
    }

    static FeedRebuildStatus succeeded(int generation, int replayed, int skipped, int unreadable, Instant startedAt, Instant finishedAt) {
        return new FeedRebuildStatus("SUCCEEDED", generation, replayed, skipped, unreadable, startedAt, finishedAt, null);
    }

    static FeedRebuildStatus failed(int generation, int replayed, int skipped, int unreadable, Instant startedAt, Instant finishedAt,
                                    String error) {
        return new FeedRebuildStatus("FAILED", generation, replayed, skipped, unreadable, startedAt, finishedAt, error);
    }

    FeedRebuildStatus withProgress(int replayedSoFar, int skippedSoFar, int unreadableSoFar) {
        return new FeedRebuildStatus(state, generation, replayedSoFar, skippedSoFar, unreadableSoFar, startedAt, finishedAt, error);
    }

    public boolean running() {
        return "RUNNING".equals(state);
    }
}
