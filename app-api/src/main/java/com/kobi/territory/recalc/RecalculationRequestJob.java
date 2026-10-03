package com.kobi.territory.recalc;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.application.RecalculateService;
import com.kobi.territory.wardrobe.application.InventoryRecalculateService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재계산 예약 배치(4단계 — 병합 뒤 진행·인벤토리 재계산). 예약된 탐험가마다 진행 → 인벤토리 순으로 "보류 규칙을 만족할 때만" 재계산하고
 * (미전달 이벤트가 있으면 다음 주기에 다시 — 보류 규칙 준수), 둘 다 끝나면 예약을 지운다. 인벤토리는 진행(도감 완성 기록)을 읽으므로 진행이
 * 먼저다. 실패는 그 탐험가만 다음 주기로 미룬다(attempts 증가, 로그). 조립 모듈의 호출 순서만 있고 규칙은 각 재계산 서비스가 갖는다.
 */
@Component
@ConditionalOnProperty(prefix = "territory.recalculation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RecalculationRequestJob {

    private static final Logger log = LoggerFactory.getLogger(RecalculationRequestJob.class);
    private static final int PAGE = 50;

    private final RecalculationRequestRepository requests;
    private final RecalculateService progression;
    private final InventoryRecalculateService inventory;
    private final TransactionTemplate tx;

    public RecalculationRequestJob(RecalculationRequestRepository requests, RecalculateService progression,
                                   InventoryRecalculateService inventory, PlatformTransactionManager transactionManager) {
        this.requests = requests;
        this.progression = progression;
        this.inventory = inventory;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${territory.recalculation.delay-ms:2000}")
    public void run() {
        List<RecalculationRequestEntity> pending = tx.execute(status -> requests.findByOrderByRequestedAtAsc(Limit.of(PAGE)));
        if (pending == null) return;
        pending.forEach(this::process);
    }

    private void process(RecalculationRequestEntity request) {
        ExplorerId explorerId = ExplorerId.of(request.explorerId());
        try {
            if (progression.recalculateIfSettled(explorerId) && inventory.recalculateIfSettled(explorerId)) {
                tx.executeWithoutResult(status -> requests.deleteIfUnchanged(request.explorerId(), request.generation()));
                log.info("재계산 예약 처리: {}", explorerId);
            }
        } catch (RuntimeException exception) {
            tx.executeWithoutResult(status -> requests.countAttempt(request.explorerId()));
            log.warn("재계산 예약 실패(다음 주기에 다시): {} — {}", explorerId, exception.toString());
        }
    }
}
