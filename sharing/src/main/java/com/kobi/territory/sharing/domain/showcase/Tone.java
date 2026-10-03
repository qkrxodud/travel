package com.kobi.territory.sharing.domain.showcase;

/** 카드 지도의 칠 색 역할(색상 값은 렌더러가 정한다). */
public enum Tone {
    /** 내 영토 */
    MINE,
    /** 강조(최근 여행 카드의 그 지역) */
    HIGHLIGHT,
    /** 흐리게(리캡의 지난 해 영토) */
    FADED,
    /** 둘 다(VS) */
    BOTH,
    /** 상대만(VS) */
    THEIRS
}
