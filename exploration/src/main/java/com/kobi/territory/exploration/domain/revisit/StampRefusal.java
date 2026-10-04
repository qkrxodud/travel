package com.kobi.territory.exploration.domain.revisit;

/** 재방문 도장을 지금 받을 수 없는 이유(화면 안내용 — 판정 순서대로). */
public enum StampRefusal {
    /** 아직 칠하지 않은 지역(탐험가 단위로 보이는 방문이 없음). */
    NOT_PAINTED,
    /** 처음 칠한 해와 같은 해 — 다음 해부터 받을 수 있다. */
    SAME_YEAR,
    /** 올해 이 지역 도장을 이미 받았다. */
    ALREADY_STAMPED,
    /** 오늘 하루 체크인 상한(도장 포함)에 닿았다. */
    DAILY_CAP
}
