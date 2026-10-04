package com.kobi.territory.analytics.domain.actor;

/**
 * 지표가 "한 사람"으로 세는 열쇠. 탐험가로 이어지면 탐험가 해시, 아직 탐험가가 없는 방문은 {@code v:}+방문 ID.
 * 방문이 나중에 탐험가로 이어지면 그 방문의 예전 이벤트도 탐험가 해시로 다시 묶는다(같은 날 두 사람으로 세지 않게).
 */
public record ActorKey(String value) {

    private static final String VISITOR_PREFIX = "v:";

    public ActorKey {
        if (value == null || value.isBlank() || value.length() > 72) throw new IllegalArgumentException("행위자 열쇠가 비었거나 너무 길다");
    }

    public static ActorKey ofExplorer(ExplorerHash explorerHash) {
        return new ActorKey(explorerHash.value());
    }

    public static ActorKey ofVisitor(VisitorId visitorId) {
        return new ActorKey(VISITOR_PREFIX + visitorId.value());
    }

    /** 탐험가가 있으면 탐험가, 없으면 방문. */
    public static ActorKey of(ExplorerHash explorerHash, VisitorId visitorId) {
        return explorerHash != null ? ofExplorer(explorerHash) : ofVisitor(visitorId);
    }
}
