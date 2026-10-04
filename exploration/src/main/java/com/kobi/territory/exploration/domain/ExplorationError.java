package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.error.ErrorKind;

/** 탐험 컨텍스트 오류 코드. code 문자열은 API 계약이다(클라이언트가 분기에 사용). */
public enum ExplorationError {
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
    ALREADY_MEMBER(ErrorKind.CONFLICT, "이미 이 지도의 멤버입니다."),
    OWNER_ONLY(ErrorKind.FORBIDDEN, "지도장만 할 수 있어요."),
    OWNER_CANNOT_LEAVE(ErrorKind.RULE_VIOLATION, "지도장은 다른 멤버에게 지도장을 넘긴 뒤 탈퇴할 수 있어요."),
    PERSONAL_MAP_ONLY_ME(ErrorKind.RULE_VIOLATION, "개인 지도에서는 할 수 없어요: %s"),
    INVITE_CODE_NOT_FOUND(ErrorKind.NOT_FOUND, "초대코드를 찾을 수 없어요: %s"),
    PROFILE_MAP_NOT_FOUND(ErrorKind.NOT_FOUND, "프로필에서 합류할 수 있는 지도가 아니에요."),
    INVALID_SETTINGS(ErrorKind.INVALID, "지도 설정이 올바르지 않습니다: %s"),
    // ---- 4단계 계정·로그인 ----
    HANDLE_INVALID(ErrorKind.INVALID, "핸들은 영문 소문자·숫자·밑줄·하이픈 %d~%d자로, 첫 글자는 영문이나 숫자여야 해요."),
    HANDLE_RESERVED(ErrorKind.INVALID, "쓸 수 없는 핸들이에요: %s"),
    HANDLE_TAKEN(ErrorKind.CONFLICT, "이미 누가 쓰고 있는 핸들이에요: %s"),
    ACCOUNT_INVALID(ErrorKind.INVALID, "로그인 정보가 올바르지 않습니다: %s"),
    LOGIN_REQUIRED(ErrorKind.UNAUTHENTICATED, "로그인해야 할 수 있어요."),
    MERGE_NOT_ALLOWED(ErrorKind.CONFLICT, "이 탐험가는 병합할 수 없어요: %s"),
    // ---- 9단계 재방문 도장·가고 싶은 곳 ----
    // DAILY_CAP_EXCEEDED 의 도장 합산 문구(코드는 같다 — 화면 분기는 그대로, 메시지만 상황에 맞게)
    REVISIT_NOT_PAINTED(ErrorKind.RULE_VIOLATION, "아직 칠하지 않은 지역이라 재방문 도장을 받을 수 없어요: %s"),
    REVISIT_SAME_YEAR(ErrorKind.RULE_VIOLATION, "%d년에 처음 칠한 지역이라 %d년부터 재방문 도장을 받을 수 있어요."),
    REVISIT_ALREADY_STAMPED(ErrorKind.CONFLICT, "%d년 재방문 도장을 이미 받았어요."),
    WISH_ALREADY_VISITED(ErrorKind.CONFLICT, "이미 칠한 지역이라 가고 싶은 곳에 꽂을 수 없어요: %s"),
    WISHLIST_FULL(ErrorKind.RULE_VIOLATION, "가고 싶은 곳은 %d곳까지 꽂을 수 있어요.");

    private final ErrorKind kind;
    private final String template;

    ExplorationError(ErrorKind kind, String template) {
        this.kind = kind;
        this.template = template;
    }

    public ErrorKind kind() {
        return kind;
    }

    /** 하루 상한이 체크인·재방문 도장 합산으로 찼을 때의 DAILY_CAP_EXCEEDED 문구(9단계 QA P3-7). */
    public static ExplorationException dailyCapWithStamps(int cap) {
        return new ExplorationException(DAILY_CAP_EXCEEDED,
            "오늘 체크인과 재방문 도장을 합친 하루 상한(" + cap + "건)에 도달했어요. 내일 다시 해 주세요.");
    }

    public ExplorationException exception(Object... args) {
        return new ExplorationException(this, String.format(template, args));
    }
}
