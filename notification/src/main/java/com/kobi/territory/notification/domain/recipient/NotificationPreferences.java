package com.kobi.territory.notification.domain.recipient;

import com.kobi.territory.notification.domain.NotificationError;
import com.kobi.territory.notification.domain.policy.NotificationKind;

/** 종류별 알림 켜고 끄기(프로필 탭). 처음에는 모두 켜짐 — 알림 자체의 동의는 기기 구독(브라우저 권한)이 맡는다. */
public record NotificationPreferences(boolean mystery, boolean streak, boolean season) {

    public static final NotificationPreferences ALL_ON = new NotificationPreferences(true, true, true);

    /** 요청 값 → 설정. 세 값이 모두 있어야 한다(빠지면 INVALID_PUSH_PREFERENCES — 부분 수정 없음). */
    public static NotificationPreferences of(Boolean mystery, Boolean streak, Boolean season) {
        if (mystery == null || streak == null || season == null) throw NotificationError.INVALID_PUSH_PREFERENCES.exception();
        return new NotificationPreferences(mystery, streak, season);
    }

    public boolean allows(NotificationKind kind) {
        return switch (kind) {
            case WEEKLY_MYSTERY -> mystery;
            case STREAK_GUARD -> streak;
            case SEASON_START -> season;
        };
    }
}
