package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.error.ErrorKind;

/** 탐험 컨텍스트 오류 코드. code 문자열은 API 계약이다(클라이언트가 분기에 사용). */
public enum ExplorationError {
    EXPLORER_ID_REQUIRED(ErrorKind.UNAUTHENTICATED, "X-Explorer-Id 헤더로 탐험가를 알려 주세요(POST /explorers 로 발급)."),
    EXPLORER_NOT_FOUND(ErrorKind.NOT_FOUND, "탐험가를 찾을 수 없습니다. 다시 발급해 주세요."),
    MAP_NOT_FOUND(ErrorKind.NOT_FOUND, "지도를 찾을 수 없습니다: %s"),
    NOT_A_MEMBER(ErrorKind.FORBIDDEN, "이 지도의 멤버가 아닙니다."),
    REGION_NOT_FOUND(ErrorKind.NOT_FOUND, "모르는 지역입니다: %s"),
    REGION_RETIRED(ErrorKind.RULE_VIOLATION, "행정구역 개편으로 폐지된 지역이라 새로 칠할 수 없어요: %s"),
    DUPLICATE_VISIT(ErrorKind.CONFLICT, "이미 칠한 지역입니다: %s"),
    VISIT_NOT_FOUND(ErrorKind.NOT_FOUND, "방문 기록이 없는 지역입니다: %s"),
    FUTURE_VISIT_DATE(ErrorKind.RULE_VIOLATION, "방문일은 오늘(%s) 이후로 적을 수 없어요."),
    DAILY_CAP_EXCEEDED(ErrorKind.RULE_VIOLATION, "하루 체크인 상한(%d곳)에 도달했어요. 내일 다시 칠해 주세요."),
    PHOTO_REQUIRED(ErrorKind.RULE_VIOLATION, "이 지도는 사진이 있어야 체크인할 수 있어요."),
    MEMO_TOO_LONG(ErrorKind.INVALID, "메모는 %d자 이하로 적어 주세요."),
    INVALID_VISIT_DATE(ErrorKind.INVALID, "방문일이 필요합니다."),
    INVALID_PHOTO_REF(ErrorKind.INVALID, "사진 주소가 올바르지 않습니다."),
    INVALID_MAP(ErrorKind.INVALID, "지도 정보가 올바르지 않습니다: %s"),
    MAP_FULL(ErrorKind.CONFLICT, "지도 멤버는 %d명까지입니다."),
    ALREADY_MEMBER(ErrorKind.CONFLICT, "이미 이 지도의 멤버입니다.");

    private final ErrorKind kind;
    private final String template;

    ExplorationError(ErrorKind kind, String template) {
        this.kind = kind;
        this.template = template;
    }

    public ErrorKind kind() {
        return kind;
    }

    public ExplorationException exception(Object... args) {
        return new ExplorationException(this, String.format(template, args));
    }
}
