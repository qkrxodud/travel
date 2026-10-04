package com.kobi.territory.notification.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.domain.delivery.PushDelivery;
import com.kobi.territory.notification.domain.delivery.PushDeliveryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 발송 기록 조회(local 확인·운영 점검용). */
@Service
public class PushDeliveryLog {

    private final PushDeliveryRepository deliveries;

    public PushDeliveryLog(PushDeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    @Transactional(readOnly = true)
    public List<PushDelivery> recentOf(ExplorerId explorerId, int limit) {
        return deliveries.recentOf(explorerId, limit);
    }
}
