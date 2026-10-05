package com.kobi.territory.catalog.domain.lineup;

/** 축제 자료를 읽지 못한 까닭. 메시지에는 키 값을 넣지 않는다. */
public enum FetchFailure {
    NOT_CONFIGURED("TourAPI 서비스 키(TOURAPI_SERVICE_KEY)가 설정되지 않았습니다"),
    KEY_REJECTED("TourAPI 가 서비스 키를 거절했습니다(미등록·만료·활용 신청 승인 전 — 공공데이터포털에서 확인)"),
    QUOTA_EXCEEDED("TourAPI 하루 호출 한도를 넘었습니다(내일 다시 시도)"),
    BAD_RESPONSE("TourAPI 응답을 읽을 수 없습니다"),
    UNREACHABLE("TourAPI 에 연결하지 못했습니다(시간 초과·네트워크)");

    private final String message;

    FetchFailure(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
