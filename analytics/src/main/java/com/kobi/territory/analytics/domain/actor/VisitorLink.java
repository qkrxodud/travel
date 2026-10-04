package com.kobi.territory.analytics.domain.actor;

import java.util.Optional;

/**
 * 방문을 기록한 뒤의 연결 상태.
 *
 * @param linkedExplorer 이 방문에 이어진 탐험가(처음 이어진 탐험가, 없으면 null)
 * @param byRequester    이어진 탐험가가 바로 이 요청의 탐험가인지 — 그렇다면 이 방문의 방문 열쇠로 남은 이벤트를 탐험가로 다시 묶는다
 *                       (처음 이어질 때 + 연결 직전에 익명으로 보낸 묶음이 늦게 커밋된 경우까지. 다시 묶을 것이 없으면 아무 일도 없다)
 */
public record VisitorLink(ExplorerHash linkedExplorer, boolean byRequester) {

    /** 방문을 적지 않은 경우(봇) — 이어진 탐험가 없음. */
    public static final VisitorLink NONE = new VisitorLink(null, false);

    /** 이 요청의 탐험가가 이 방문의 탐험가면, 방문 열쇠로 남은 이벤트를 다시 묶을 열쇠(탐험가 해시). */
    public Optional<ActorKey> relinkTarget() {
        return byRequester && linkedExplorer != null ? Optional.of(ActorKey.ofExplorer(linkedExplorer)) : Optional.empty();
    }

    /** 이 요청의 이벤트를 누구로 셀지: 요청의 탐험가 → 이 방문에 이어진 탐험가 → 방문 자신. */
    public ActorKey actorFor(VisitorId visitorId, ExplorerHash requestExplorer) {
        if (requestExplorer != null) return ActorKey.ofExplorer(requestExplorer);
        return ActorKey.of(linkedExplorer, visitorId);
    }
}
