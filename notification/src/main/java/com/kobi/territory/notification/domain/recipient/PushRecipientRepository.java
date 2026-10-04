package com.kobi.territory.notification.domain.recipient;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.domain.policy.NotificationKind;
import com.kobi.territory.notification.domain.push.PushEndpoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 알림 받는 사람 저장소 포트(push_recipient 루트 + push_device). */
public interface PushRecipientRepository {

    /** 루트 행이 없으면 넣는다(동시에 넣어 생기는 유일성 위반은 호출자가 목표 상태로 흡수한다). */
    void ensure(ExplorerId explorerId, Instant at);

    /** 루트 행 배타 잠금(SELECT … FOR UPDATE) 뒤 기기까지. 없으면 빈 값. */
    Optional<PushRecipient> findLocked(ExplorerId explorerId);

    Optional<PushRecipient> find(ExplorerId explorerId);

    /** 설정을 고치고, 기기는 지금 것을 넣거나 고치고 뺀 기기는 지운다. */
    void save(PushRecipient recipient);

    /** 같은 브라우저 구독이 다른 탐험가에게 묶여 있으면 그 기기를 푼다(한 브라우저 = 지금 로그인한 한 사람). */
    void releaseEndpoint(PushEndpoint endpoint, ExplorerId keeper);

    /** 그 종류를 켜 두고 기기가 하나 이상 있는 사람(탐험가 id 순, after 다음부터 limit 명 — 대량 발송 쪽 나누기). */
    List<ExplorerId> reachable(NotificationKind kind, ExplorerId after, int limit);
}
