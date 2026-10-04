package com.kobi.territory.notification.domain.policy;

import java.util.Arrays;
import java.util.Optional;

/** 알림 종류. code 는 API·설정·분석 이벤트(push_sent·push_open 의 kind)에 쓰는 이름이라 바꾸지 않는다. */
public enum NotificationKind {
    /** 월요일 오전 "이번 주 미스터리 지역"(지역 이름은 감춘다). */
    WEEKLY_MYSTERY("mystery"),
    /** 월말 며칠 전, 이번 달 새 지역이 없어 연속 탐험이 끊길 탐험가에게 "스트릭 지키기". */
    STREAK_GUARD("streak"),
    /** 계절 한정 테마 시작일. */
    SEASON_START("season");

    private final String code;

    NotificationKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<NotificationKind> ofCode(String code) {
        return Arrays.stream(values()).filter(kind -> kind.code.equals(code)).findFirst();
    }
}
