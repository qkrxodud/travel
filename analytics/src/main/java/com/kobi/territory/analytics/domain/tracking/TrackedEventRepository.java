package com.kobi.territory.analytics.domain.tracking;

import com.kobi.territory.analytics.domain.actor.ActorKey;
import com.kobi.territory.analytics.domain.actor.VisitorId;
import java.time.LocalDate;
import java.util.List;

/** 원본 이벤트 저장 포트. */
public interface TrackedEventRepository {

    /** 화면 이벤트·요청 이벤트를 적는다(지문 없음). */
    void appendAll(List<TrackedEvent> events);

    /** 서버 사실을 한 번만 적는다 — 같은 지문이 이미 있으면 적지 않고 false(재전달 멱등). */
    boolean appendOnce(TrackedEvent event);

    /** 방문이 처음 탐험가로 이어졌을 때, 그 방문의 예전 이벤트도 탐험가로 센다. @return 바뀐 줄 수 */
    int relinkVisitor(VisitorId visitorId, ActorKey explorerActor);

    /** before 이전 날짜의 원본을 지운다(어댑터가 조각으로 나눠 짧게). @return 지운 줄 수 */
    int purgeBefore(LocalDate before);
}
