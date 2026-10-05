package com.kobi.territory.catalog.domain.lineup;

/** 누가(어떻게) 회차 지역 목록을 확정했는지. 관리자가 확정한 회차는 자동 수집이 덮지 않는다. */
public enum ConfirmedBy {
    /** 자동 수집이 근거가 충분해 확정. */
    AUTO,
    /** 관리자가 확정. */
    ADMIN,
    /** 확정 없이 회차가 열려, 그때 쓰던 기본 목록(AI 추정)을 그대로 고정(스냅숏) — 뒤에 seasons.json 을 고쳐도 이 회차는 바뀌지 않는다. */
    OPENING
}
