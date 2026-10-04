package com.kobi.territory.notification.domain.campaign;

import com.kobi.territory.notification.domain.policy.NotificationKind;
import com.kobi.territory.notification.domain.policy.QuietHours;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 알림 달력(정책 VO) — 언제 어떤 알림을 보내는가. 스케줄(cron, 설정값)이 깨우면 오늘이 그 알림의 날인지 판단하고 캠페인을 만든다. 보낼 시각은
 * 조용한 시간을 피한다(그 시간대면 다음 허용 시각). local 즉시 발송({@code force})은 날짜 조건과 조용한 시간을 건너뛴다(동의·설정·하루 최대
 * 개수·멱등은 그대로).
 * <ul>
 *   <li>미스터리: 그 주 월요일(서울)에만. 기간 = 월요일 날짜</li>
 *   <li>스트릭 지키기: 그 달 마지막 날 − streakDaysBeforeMonthEnd 일(기본 3 — 10월이면 28일)에만. 기간 = 그 달</li>
 *   <li>계절: 계절 테마 시작일에만. 기간 = 회차 id</li>
 * </ul>
 */
public record CampaignCalendar(QuietHours quietHours, int streakDaysBeforeMonthEnd) {

    public CampaignCalendar {
        Objects.requireNonNull(quietHours, "quietHours");
        if (streakDaysBeforeMonthEnd < 0 || streakDaysBeforeMonthEnd > 27) {
            throw new IllegalArgumentException("streak-days-before-month-end 는 0~27");
        }
    }

    public Optional<Campaign> weeklyMystery(LocalDate weekStart, Instant now, boolean force) {
        if (!force && !today(now).equals(weekStart)) return Optional.empty();
        return Optional.of(campaign(NotificationKind.WEEKLY_MYSTERY, weekStart.toString(), now, force));
    }

    public Optional<Campaign> streakGuard(Instant now, boolean force) {
        LocalDate today = today(now);
        if (!force && !today.equals(streakReminderDay(YearMonth.from(today)))) return Optional.empty();
        return Optional.of(campaign(NotificationKind.STREAK_GUARD, YearMonth.from(today).toString(), now, force));
    }

    /** 오늘 시작하는 계절마다 캠페인 하나(같은 날 둘이면 하루 최대 개수가 한 사람에게 하나만 남긴다). */
    public List<SeasonCampaign> seasonStarts(SeasonStarts seasons, Instant now, boolean force) {
        LocalDate today = today(now);
        if (force) {
            return seasons.openOrNext(today).map(round -> List.of(new SeasonCampaign(
                campaign(NotificationKind.SEASON_START, round.roundId(), now, true), round.season()))).orElse(List.of());
        }
        return seasons.startingOn(today).stream()
            .map(season -> new SeasonCampaign(campaign(NotificationKind.SEASON_START, season.roundId(today.getYear()), now, false),
                season))
            .toList();
    }

    /** 그 달의 스트릭 지키기 날. */
    public LocalDate streakReminderDay(YearMonth month) {
        return month.atEndOfMonth().minusDays(streakDaysBeforeMonthEnd);
    }

    /** 스트릭 지키기 날 기준 그 달이 끝나기까지 남은 날 수(마지막 날 포함 — 28일이면 31일까지 4일). */
    public int daysLeftInMonth(Instant now) {
        LocalDate today = today(now);
        return (int) ChronoUnit.DAYS.between(today, YearMonth.from(today).atEndOfMonth()) + 1;
    }

    private Campaign campaign(NotificationKind kind, String period, Instant now, boolean force) {
        Instant dueAt = force ? now : quietHours.nextAllowed(now);
        return new Campaign(kind, period, quietHours.dayOf(dueAt), dueAt, force);
    }

    private LocalDate today(Instant now) {
        return quietHours.dayOf(now);
    }

    /** 계절 캠페인과 그 계절(문구용). */
    public record SeasonCampaign(Campaign campaign, SeasonStart season) {}
}
