package com.kobi.territory.config;

import com.kobi.territory.common.event.EventBacklog;
import com.kobi.territory.progression.application.RecalculateService;
import com.kobi.territory.wardrobe.application.InventoryRecalculateService;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 재계산 배치의 운영 진입점(관리용). HTTP 로 열지 않고 기동 인자로만 켠다:
 * {@code java -jar territory.jar --territory.progression.recalculate-on-startup=true}. local 은 POST /dev/recalculate.
 * 릴레이가 outbox 를 비운 뒤 실행한다(S3-3) — recalculate-wait 동안 기다려도 남아 있으면 그대로 돌리고, 미전달 이벤트가
 * 남은 탐험가는 보류(deferred)로 보고된다. 트래픽 적은 시간에 돌린다(실행 중 칭호 선택은 409 로 실패할 수 있다 — S3-2).
 */
@Component
@ConditionalOnProperty(name = "territory.progression.recalculate-on-startup", havingValue = "true")
class RecalculateOnStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecalculateOnStartup.class);

    private final RecalculateService recalculate;
    private final InventoryRecalculateService inventoryRecalculate;
    private final EventBacklog backlog;
    private final Duration wait;

    RecalculateOnStartup(RecalculateService recalculate, InventoryRecalculateService inventoryRecalculate,
                         EventBacklog backlog, @Value("${territory.progression.recalculate-wait:5m}") Duration wait) {
        this.recalculate = recalculate;
        this.inventoryRecalculate = inventoryRecalculate;
        this.backlog = backlog;
        this.wait = wait;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        Instant deadline = Instant.now().plus(wait);
        while (backlog.hasAnyPending() && Instant.now().isBefore(deadline)) {
            Thread.sleep(1000);
        }
        if (backlog.hasAnyPending()) log.warn("outbox 가 {} 안에 비지 않았다 — 미전달 이벤트가 남은 탐험가는 보류된다", wait);
        recalculate.recalculateAll();
        // 3단계: 인벤토리 재계산(진행·도감 다음 — 완성 테마를 진행 Query 로 읽는다)
        inventoryRecalculate.recalculateAll();
    }
}
