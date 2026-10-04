package com.kobi.territory.notification.domain.delivery;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 발송 기록 저장소 포트(push_delivery — UNIQUE(explorer_id, kind, period)). */
public interface PushDeliveryRepository {

    /** 그 사람의 그날 기록들. */
    List<PushDelivery> onDay(ExplorerId explorerId, LocalDate day);

    /** 같은 열쇠의 기록이 있는지(날짜와 상관없이). */
    boolean exists(DeliveryKey key);

    /** 새 기록을 넣는다(같은 열쇠가 있으면 저장 기술의 유일성 위반이 그대로 올라간다). */
    void add(PushDelivery delivery);

    /** 바뀐 상태를 쓴다 — 읽은 뒤 다른 쪽이 먼저 고쳤으면 낙관적 잠금 충돌이 그대로 올라간다. */
    void update(PushDelivery delivery);

    Optional<PushDelivery> find(long id);

    /** 보낼 때가 된 PENDING 과 staleBefore 전에 잡힌 채 멈춘 SENDING 의 id(보낼 시각 순, limit 개). */
    List<Long> due(Instant now, Instant staleBefore, int limit);

    /** 그 사람의 최근 기록(dev 확인용, 최근 것 먼저). */
    List<PushDelivery> recentOf(ExplorerId explorerId, int limit);
}
