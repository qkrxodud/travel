package com.kobi.territory.notification.domain.delivery;

/** 잡았던 발송 기록을 닫으려는데 그새 다른 발송기가 다시 잡았거나 이미 닫혔다(보내는 중에 claim-timeout 을 넘김). 결과를 쓰지 않는다. */
public class DeliveryReclaimed extends RuntimeException {

    public DeliveryReclaimed(DeliveryKey key, DeliveryStatus status) {
        super("발송 기록을 다른 발송기가 다시 잡았다: " + key.kind().code() + "/" + key.period() + " 상태 " + status);
    }
}
