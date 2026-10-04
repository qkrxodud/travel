package com.kobi.territory.analytics.domain.tracking;

/** 이벤트가 온 곳. */
public enum EventSource {
    /** 화면이 보낸 이벤트({@code POST /events}) — 사용자가 본 것·누른 것 */
    CLIENT,
    /** 서버의 확실한 사실(다른 컨텍스트의 공개 이벤트) — 가입·체크인·합류 등 */
    SERVER,
    /** 공개 페이지 요청 자체(공개 프로필·자랑 카드 열람) — 요청 필터가 적는다 */
    REQUEST
}
