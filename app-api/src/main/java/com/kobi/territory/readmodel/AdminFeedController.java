package com.kobi.territory.readmodel;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영: 친구 소식 읽기 모델 재구성(5단계 리더 결정 4). /admin/** 는 X-Admin-Token(AdminWebConfig). 5단계 배포 직후 한 번(새 구독자
 * social.feed 는 이미 발행된 이벤트를 받지 않는다), 이후 투영 버그 복구용.
 * <ul>
 *   <li>{@code POST /admin/rebuild/feed} — 202 상태(시작) / 409 상태(이미 진행 중)</li>
 *   <li>{@code GET /admin/rebuild/feed} — 200 상태(IDLE·RUNNING·SUCCEEDED·FAILED, 재생·건너뜀 수)</li>
 * </ul>
 */
@RestController
public class AdminFeedController {

    private final FeedRebuildJob job;

    public AdminFeedController(FeedRebuildJob job) {
        this.job = job;
    }

    @PostMapping("/admin/rebuild/feed")
    public ResponseEntity<FeedRebuildStatus> start() {
        FeedRebuildJob.Started started = job.start();
        return ResponseEntity.status(started.started() ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT).body(started.status());
    }

    @GetMapping("/admin/rebuild/feed")
    public FeedRebuildStatus status() {
        return job.current();
    }
}
