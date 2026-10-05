package com.kobi.territory.catalog.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 계절 회차 자동 수집(13s단계) — 하루 한 번(territory.tourapi.collect.cron, "-" 면 끔)과 기동 직후 한 번(collect-on-startup). 수집은 키가 없으면
 * 하지 않는다. 그 앞에 열린 회차 스냅숏(확정본 없이 열린 회차를 기본 목록으로 고정)은 키와 무관하게 늘 한다. 기동 직후 일은 기동을 붙잡지 않게 따로 돈다.
 */
@Component
class SeasonLineupScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeasonLineupScheduler.class);

    private final SeasonLineupService lineups;
    private final SeasonLineupSettings settings;

    SeasonLineupScheduler(SeasonLineupService lineups, SeasonLineupSettings settings) {
        this.lineups = lineups;
        this.settings = settings;
    }

    @Scheduled(cron = "${territory.tourapi.collect.cron:0 40 4 * * *}", zone = "${territory.time-zone:Asia/Seoul}")
    void daily() {
        run();
    }

    /** 기동 직후: 열린 회차 스냅숏(키와 무관 — 기동 시 보정), 키가 있고 켜져 있으면 자동 수집도. */
    @EventListener(ApplicationReadyEvent.class)
    void afterStartup() {
        boolean collect = settings.collectOnStartup() && lineups.configured();
        Thread.ofVirtual().name("season-lineup-startup").start(collect ? this::run : this::snapshot);
    }

    private void snapshot() {
        try {
            lineups.snapshotOpenRounds();
        } catch (RuntimeException unexpected) {
            log.warn("열린 계절 회차 스냅숏 실패: {}", unexpected.getClass().getSimpleName());
        }
    }

    private void run() {
        snapshot();
        try {
            lineups.collectAutomatically();
        } catch (RuntimeException unexpected) {
            log.warn("계절 회차 자동 수집 실패: {}", unexpected.getClass().getSimpleName());
        }
    }
}
