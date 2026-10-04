package com.kobi.territory.notification.domain.campaign;

import com.kobi.territory.notification.domain.policy.NotificationKind;
import com.kobi.territory.notification.domain.push.PushMessage;
import java.time.LocalDate;

/**
 * 알림 문구와 눌렀을 때 열 경로. 경로에는 {@code from=push}·{@code push=종류}를 붙인다 — 화면이 열릴 때 분석 이벤트 {@code push_open}
 * 과 {@code app_open(entry=push)} 를 보낸다. 미스터리 지역 이름·handle 같은 개인정보는 넣지 않는다.
 */
public final class CampaignMessages {

    private CampaignMessages() {}

    public static PushMessage weeklyMystery(LocalDate weekStart) {
        return new PushMessage(NotificationKind.WEEKLY_MYSTERY, "❓ 이번 주 미스터리 지역이 정해졌어요",
            "어디인지는 비밀 — 희귀한 곳 한 곳이 숨어 있어요. 찾아내면 보너스 XP!", path(NotificationKind.WEEKLY_MYSTERY, "map"),
            "mystery-" + weekStart);
    }

    /** 스트릭 지키기 — 연속 개월·남은 날·보호권 안내. */
    public static PushMessage streakGuard(StreakFacts facts, int daysLeft, String month) {
        String freezes;
        if (facts.coveredIfMissed()) {
            freezes = "놓쳐도 보호권 " + facts.freezesHeld() + "개 중 " + facts.freezesNeededIfMissed() + "개로 지킬 수 있지만 아껴 두세요.";
        } else if (facts.freezesHeld() > 0) {
            freezes = "보호권 " + facts.freezesHeld() + "개로는 모자라 놓치면 연속이 끊겨요.";
        } else {
            freezes = "보호권이 없어 놓치면 연속이 끊겨요.";
        }
        return new PushMessage(NotificationKind.STREAK_GUARD, "🔥 " + facts.months() + "개월 연속 탐험을 지켜요",
            "이번 달이 " + daysLeft + "일 남았어요. 새 지역 한 곳이면 이어져요. " + freezes, path(NotificationKind.STREAK_GUARD, "map"),
            "streak-" + month);
    }

    public static PushMessage seasonStart(SeasonStart season, String roundId) {
        String emoji = season.emoji().isBlank() ? "" : season.emoji() + " ";
        return new PushMessage(NotificationKind.SEASON_START, emoji + season.name() + " 시즌이 시작됐어요",
            season.end().getMonthValue() + "월 " + season.end().getDayOfMonth() + "일까지 모두 칠하면 계절 한정 보상을 받아요.",
            path(NotificationKind.SEASON_START, "sets"), "season-" + roundId);
    }

    private static String path(NotificationKind kind, String tab) {
        return "/?from=push&push=" + kind.code() + "#" + tab;
    }
}
