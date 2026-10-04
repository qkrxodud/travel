package com.kobi.territory.analytics.application;

import com.kobi.territory.analytics.domain.ratelimit.ClientOrigin;
import com.kobi.territory.analytics.domain.tracking.SubmittedEvent;
import java.util.List;

/**
 * 화면 이벤트 묶음 받기 커맨드.
 *
 * @param visitorId     익명 방문 ID 원문(검증 전)
 * @param explorerId    토큰·세션으로 확인된 탐험가 id(없으면 null) — 해시로만 적는다
 * @param userAgent     기기 유형 판단에만 쓰고 저장하지 않는다
 * @param origin        요청이 온 곳(접속 주소·프록시 헤더) — 레이트 리밋 주소·나라 판단에만 쓰고 저장하지 않는다
 */
public record ClientEvents(String visitorId, String explorerId, List<SubmittedEvent> events, String userAgent, ClientOrigin origin) {

    public ClientEvents {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
