package com.kobi.territory.catalog.domain.lineup;

/** 자동 수집이 지금 그 회차에 할 일. */
public enum CollectionPlan {
    /** 아무것도 안 한다(이미 열림·관리자 확정·최근에 모음·아직 이름). */
    NONE,
    /** 미리보기 후보만 모은다(수집 기간 전이지만 한 번도 모은 적 없음 — 키를 넣고 재기동하면 바로 보인다). */
    PREVIEW,
    /** 후보를 모은다(자동 확정 꺼짐). */
    COLLECT,
    /** 후보를 모으고 조건이 맞으면 확정한다. */
    COLLECT_AND_CONFIRM
}
