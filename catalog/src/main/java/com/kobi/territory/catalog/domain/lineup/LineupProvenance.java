package com.kobi.territory.catalog.domain.lineup;

/** 회차 지역 하나의 출처. code 는 API 계약 값이다. */
public enum LineupProvenance {
    /** 한국관광공사 TourAPI 행사정보(축제)가 근거. */
    TOURAPI("tourapi"),
    /** AI 가 일반 지식으로 추정한 기본 목록(seasons.json, 검증 전). */
    AI_ESTIMATE("ai-estimate");

    /** 근거 출처 표기(화면·관리자). */
    public static final String TOURAPI_SOURCE = "한국관광공사 TourAPI";

    private final String code;

    LineupProvenance(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static LineupProvenance ofCode(String code) {
        for (LineupProvenance provenance : values()) {
            if (provenance.code.equals(code)) return provenance;
        }
        throw new IllegalArgumentException("모르는 출처: " + code);
    }
}
