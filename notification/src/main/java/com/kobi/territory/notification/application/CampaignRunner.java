package com.kobi.territory.notification.application;

import com.kobi.territory.catalog.api.query.MysteryRegionQuery;
import com.kobi.territory.catalog.api.query.MysteryWeekView;
import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.domain.campaign.Campaign;
import com.kobi.territory.notification.domain.campaign.CampaignCalendar;
import com.kobi.territory.notification.domain.campaign.CampaignMessages;
import com.kobi.territory.notification.domain.campaign.SeasonStart;
import com.kobi.territory.notification.domain.campaign.SeasonStarts;
import com.kobi.territory.notification.domain.campaign.StreakFacts;
import com.kobi.territory.notification.domain.delivery.PlanDecision;
import com.kobi.territory.notification.domain.policy.NotificationKind;
import com.kobi.territory.notification.domain.push.PushMessage;
import com.kobi.territory.notification.domain.recipient.PushRecipientRepository;
import com.kobi.territory.progression.api.query.StreakQuery;
import com.kobi.territory.progression.api.query.StreakStandingView;
import java.time.Clock;
import java.time.Instant;
import java.time.MonthDay;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 알림 3종 스케줄(cron 설정값, 서비스 시간대 Asia/Seoul — "-" 면 그 알림은 끔). 깨어나면 오늘이 그 알림의 날인지 달력({@link CampaignCalendar})에
 * 묻고, 그 종류를 켜 두고 기기가 있는 사람을 탐험가 id 순으로 pageSize 명씩 불러와 한 사람씩 계획한다(사람마다 짧은 트랜잭션). 실제 발송은
 * {@link DeliveryDispatcher} 가 보낼 시각에 한다. 같은 날 다시 돌아도 멱등(같은 열쇠는 한 번).
 * <p>
 * 같은 날 두 알림이 겹치면 먼저 계획된 알림만 남는다(하루 최대 1개) — 기본 cron 은 계절(08:30) → 미스터리(월 09:00) → 스트릭(19:00) 순이다.
 */
@Component
public class CampaignRunner {

    private static final Logger log = LoggerFactory.getLogger(CampaignRunner.class);

    private final PushRecipientRepository recipients;
    private final DeliveryPlanningService planning;
    private final MysteryRegionQuery mysteries;
    private final StreakQuery streaks;
    private final SeasonStarts seasons;
    private final NotificationSettings settings;
    private final Clock clock;

    public CampaignRunner(PushRecipientRepository recipients, DeliveryPlanningService planning, MysteryRegionQuery mysteries,
                          StreakQuery streaks, ProgressionRules rules, NotificationSettings settings, Clock clock) {
        this.recipients = recipients;
        this.planning = planning;
        this.mysteries = mysteries;
        this.streaks = streaks;
        this.settings = settings;
        this.clock = clock;
        this.seasons = SeasonStarts.of(rules.seasons().stream()
            .map(season -> new SeasonStart(season.id(), season.name(), season.emoji(), MonthDay.parse("--" + season.start()),
                MonthDay.parse("--" + season.end())))
            .toList());
    }

    @Scheduled(cron = "${territory.push.schedule.mystery-cron:0 0 9 * * MON}", zone = "${territory.time-zone:Asia/Seoul}")
    public void scheduledMystery() {
        logRun(weeklyMystery(false));
    }

    @Scheduled(cron = "${territory.push.schedule.streak-cron:0 0 19 * * *}", zone = "${territory.time-zone:Asia/Seoul}")
    public void scheduledStreak() {
        logRun(streakGuard(false));
    }

    @Scheduled(cron = "${territory.push.schedule.season-cron:0 30 8 * * *}", zone = "${territory.time-zone:Asia/Seoul}")
    public void scheduledSeason() {
        seasonStarts(false).forEach(this::logRun);
    }

    /** 이번 주 미스터리 지역 알림(월요일에만, force 면 언제든) — 지역 이름은 싣지 않는다. */
    public CampaignRun weeklyMystery(boolean force) {
        Instant now = clock.instant();
        MysteryWeekView week = mysteries.thisWeek();
        PushMessage message = CampaignMessages.weeklyMystery(week.weekStart());
        return settings.calendar().weeklyMystery(week.weekStart(), now, force)
            .map(campaign -> fanOut(campaign, page -> page.stream().map(explorerId -> new Target(explorerId, message)).toList()))
            .orElseGet(() -> CampaignRun.notToday(NotificationKind.WEEKLY_MYSTERY));
    }

    /** 스트릭 지키기(그 달 마지막 날 − N 일에만, force 면 언제든) — 이번 달을 놓치면 끊길 수 있는 사람에게만, 보호권 수 안내. */
    public CampaignRun streakGuard(boolean force) {
        Instant now = clock.instant();
        int daysLeft = settings.calendar().daysLeftInMonth(now);
        return settings.calendar().streakGuard(now, force)
            .map(campaign -> fanOut(campaign, page -> streaks.standingsOf(page.stream().map(ExplorerId::value).toList()).stream()
                .filter(StreakStandingView::atRisk)
                .map(standing -> new Target(ExplorerId.of(standing.explorerId()), CampaignMessages.streakGuard(
                    new StreakFacts(standing.streakMonths(), standing.freezesHeld(), standing.freezesNeededIfMissed()), daysLeft,
                    campaign.period())))
                .toList()))
            .orElseGet(() -> CampaignRun.notToday(NotificationKind.STREAK_GUARD));
    }

    /** 계절 테마 시작일 알림(시작일에만, force 면 지금 열린 계절 또는 다음 계절). */
    public List<CampaignRun> seasonStarts(boolean force) {
        List<CampaignRun> runs = settings.calendar().seasonStarts(seasons, clock.instant(), force).stream()
            .map(seasonCampaign -> {
                PushMessage message = CampaignMessages.seasonStart(seasonCampaign.season(), seasonCampaign.campaign().period());
                return fanOut(seasonCampaign.campaign(),
                    page -> page.stream().map(explorerId -> new Target(explorerId, message)).toList());
            })
            .toList();
        return runs.isEmpty() ? List.of(CampaignRun.notToday(NotificationKind.SEASON_START)) : runs;
    }

    /** 받을 사람을 pageSize 명씩 불러와 대상(사람 + 문구)으로 바꾼 뒤 한 사람씩 계획한다. */
    private CampaignRun fanOut(Campaign campaign, Function<List<ExplorerId>, List<Target>> targetsOf) {
        Map<PlanDecision, Integer> decisions = new EnumMap<>(PlanDecision.class);
        int notTarget = 0;
        ExplorerId after = null;
        List<ExplorerId> page = recipients.reachable(campaign.kind(), null, settings.pageSize());
        while (!page.isEmpty()) {
            List<Target> targets = targetsOf.apply(page);
            notTarget += page.size() - targets.size();
            targets.forEach(target -> decisions.merge(plan(target, campaign), 1, Integer::sum));
            after = page.get(page.size() - 1);
            page = page.size() < settings.pageSize() ? List.of() : recipients.reachable(campaign.kind(), after, settings.pageSize());
        }
        return new CampaignRun(campaign.kind(), campaign.period(), campaign.deliveryDay(), campaign.dueAt(), decisions, notTarget);
    }

    private PlanDecision plan(Target target, Campaign campaign) {
        try {
            return planning.planFor(target.explorerId(), campaign, target.message());
        } catch (DataIntegrityViolationException plannedConcurrently) {
            return PlanDecision.ALREADY_PLANNED;  // 같은 열쇠를 다른 실행이 먼저 넣었다
        }
    }

    private void logRun(CampaignRun run) {
        if (run.period() == null) return;
        log.info("알림 계획 {} {}: {} (보낼 시각 {}, 대상 아님 {})", run.kind().code(), run.period(), run.decisions(), run.dueAt(),
            run.notTarget());
    }

    private record Target(ExplorerId explorerId, PushMessage message) {}
}
