package com.kobi.territory.progression.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.ClaimTransferred;
import com.kobi.territory.exploration.api.event.MapCreated;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.RevisitStamped;
import com.kobi.territory.exploration.api.event.WishFulfilled;
import com.kobi.territory.progression.api.event.SeasonCompleted;
import com.kobi.territory.exploration.api.event.VisitCancelled;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.progression.api.event.BadgeEarned;
import com.kobi.territory.progression.api.event.LevelUp;
import com.kobi.territory.progression.api.event.MysteryBonusEarned;
import com.kobi.territory.progression.api.event.ProvinceConquered;
import com.kobi.territory.progression.api.event.StreakMilestoneReached;
import com.kobi.territory.progression.api.event.QuestCompleted;
import com.kobi.territory.progression.api.event.SetCompleted;
import com.kobi.territory.progression.domain.progress.ExplorerProgress;
import com.kobi.territory.progression.domain.progress.ExplorerProgressRepository;
import com.kobi.territory.progression.domain.progress.ProgressChange;
import com.kobi.territory.progression.domain.progress.ProgressVisit;
import com.kobi.territory.progression.domain.quest.QuestPeriod;
import com.kobi.territory.catalog.api.query.MysteryWeekView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 진행(ExplorerProgress) 유스케이스 — "불러와서 → 도메인에 시키고 → 저장 → outbox". 판단·계산(보상·세대 refId·
 * 스트릭·레벨·뱃지·칭호)은 ExplorerProgress·XpLedger·ExploredRegions 가 한다.
 * 이벤트 처리는 릴레이가 구독자 트랜잭션(REQUIRES_NEW) 안에서 부른다. 트랜잭션 안에서는 애그리거트 로드·저장만 한다(짧게).
 */
@Service
public class ProgressService {

    static final String AGGREGATE = "ExplorerProgress";
    /** MapCreated.kind 중 개인 지도(탐험 공개 계약 값). */
    static final String PERSONAL = "PERSONAL";

    private final ExplorerProgressRepository progresses;
    private final ProgressionCatalog catalog;
    private final TerritoryQuery territories;
    private final EventOutbox outbox;
    private final Clock clock;
    private final TransactionTemplate writeTx;

    public ProgressService(ExplorerProgressRepository progresses, ProgressionCatalog catalog, TerritoryQuery territories,
                           EventOutbox outbox, Clock clock, PlatformTransactionManager transactionManager) {
        this.writeTx = new TransactionTemplate(transactionManager);
        this.progresses = progresses;
        this.catalog = catalog;
        this.territories = territories;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void onRegionVisited(RegionVisited event) {
        ExplorerProgress progress = loadLocked(ExplorerId.of(event.explorerId()));
        ProgressChange change = progress.applyVisit(visitWithMystery(event), catalog.policy());
        progresses.save(progress);
        publish(progress, change);
    }

    @Transactional
    public void onVisitCancelled(VisitCancelled event) {
        ExplorerProgress progress = loadLocked(ExplorerId.of(event.explorerId()));
        ProgressChange change = progress.revokeVisit(event.mapId(), RegionCode.of(event.regionCode()), event.visitGeneration(),
            event.cancelledAt(), catalog.policy());
        progresses.save(progress);
        publish(progress, change);
    }

    @Transactional
    public void onSetCompleted(SetCompleted event) {
        ExplorerProgress progress = loadLocked(ExplorerId.of(event.explorerId()));
        ProgressChange change = progress.applyThemeCompleted(event.setId(), event.completedAt(), catalog.policy());
        progresses.save(progress);
        publish(progress, change);
    }

    /** 계절 회차 완성(9단계, 수령자마다) → +XP·계절 칭호. 멱등. */
    @Transactional
    public void onSeasonCompleted(SeasonCompleted event) {
        ExplorerProgress progress = loadLocked(ExplorerId.of(event.explorerId()));
        ProgressChange change = progress.applySeasonCompleted(event.roundId(), event.completedAt(), catalog.policy());
        progresses.save(progress);
        publish(progress, change);
    }

    /** 재방문 도장(9단계) → +XP·"단골 여행자" 뱃지. 멱등(지역·연도당 한 번). */
    @Transactional
    public void onRevisitStamped(RevisitStamped event) {
        ExplorerProgress progress = loadLocked(ExplorerId.of(event.explorerId()));
        ProgressChange change = progress.applyRevisitStamp(RegionCode.of(event.regionCode()), event.year(), event.stampedAt(),
            catalog.policy());
        progresses.save(progress);
        publish(progress, change);
    }

    /** 가고 싶은 곳 다녀옴(9단계) → +XP·"꿈을 이룬 여행자" 뱃지. 멱등(지역당 한 번). */
    @Transactional
    public void onWishFulfilled(WishFulfilled event) {
        ExplorerProgress progress = loadLocked(ExplorerId.of(event.explorerId()));
        ProgressChange change = progress.applyWishFulfilled(RegionCode.of(event.regionCode()), event.fulfilledAt(), catalog.policy());
        progresses.save(progress);
        publish(progress, change);
    }

    /** 선점 이전 → 새 선점자(event.explorerId) 선점 보너스(+10, refId claim:{mapId}:{code}:{e}). 멱등. */
    @Transactional
    public void onClaimTransferred(ClaimTransferred event) {
        ExplorerProgress progress = loadLocked(ExplorerId.of(event.explorerId()));
        ProgressChange change = progress.applyClaimTransferred(event.mapId(), RegionCode.of(event.regionCode()), event.rarity(),
            event.transferredAt(), catalog.policy());
        progresses.save(progress);
        publish(progress, change);
    }

    /**
     * 개인 지도 생성(= 탐험가 가입) → 진행 루트 행을 미리 만든다(구조 QA S3-1 — 이후 이벤트 처리·재계산의 findLocked 가 항상
     * 있는 행을 잠가 "없는 행 잠금"의 갭 잠금 교착이 생기지 않게). 이미 있으면 아무것도 하지 않는다(멱등). 공유 지도는 해당 없음.
     */
    @Transactional
    public void onMapCreated(MapCreated event) {
        if (!PERSONAL.equals(event.kind())) return;
        ExplorerId owner = ExplorerId.of(event.ownerId());
        if (progresses.find(owner).isPresent()) return;
        progresses.save(ExplorerProgress.start(owner, catalog.policy(), event.createdAt()));
    }

    @Transactional
    public void onQuestCompleted(QuestCompleted event) {
        ExplorerProgress progress = loadLocked(ExplorerId.of(event.explorerId()));
        ProgressChange change = progress.applyQuestReward(new QuestPeriod(event.period()), event.questId(), event.xp(),
            event.claimedAt(), catalog.policy());
        progresses.save(progress);
        publish(progress, change);
    }

    /** GET /progress — 아직 이벤트가 없으면 시작 상태(XP 0, Lv.1). 탐험가가 없으면 404 EXPLORER_NOT_FOUND. */
    @Transactional(readOnly = true)
    public ExplorerProgress view(ExplorerId explorerId) {
        territories.personalMapId(explorerId.value());
        return load(explorerId);
    }

    /**
     * GET /mystery/this-week — 이번 주 미스터리 지역(없으면 카탈로그가 골라 기록)과 내가 이번 주 보너스를 받았는지(8단계).
     * 탐험가가 없으면 404 EXPLORER_NOT_FOUND.
     */
    @Transactional(readOnly = true)
    public ThisWeekMystery mysteryThisWeek(ExplorerId explorerId) {
        territories.personalMapId(explorerId.value());
        MysteryWeekView week = catalog.mysteryThisWeek();
        ExplorerProgress progress = load(explorerId);
        return new ThisWeekMystery(week, progress.mysteryFoundAt(week.weekId()).orElse(null), progress.mysteryFoundCount(),
            clock.instant());
    }

    /**
     * 이번 주 미스터리 현황.
     *
     * @param receivedAt 이번 주 보너스를 받은 시각, 아직이면 null
     * @param foundCount 지금까지 보너스를 받은 주 수
     */
    public record ThisWeekMystery(MysteryWeekView week, Instant receivedAt, int foundCount, Instant now) {
        /** 이번 주 보너스를 받았는지. */
        public boolean received() {
            return receivedAt != null;
        }

        /** 지역 이름을 바로 공개하는지 — 이번 주 보너스를 받았으면(직접 찾아 칠했다). */
        public boolean revealed() {
            return received();
        }

        /** 이번 주가 끝날 때까지 남은 초(0 이상). */
        public long remainingSeconds() {
            return Math.max(0, Duration.between(now, week.endsAt()).getSeconds());
        }
    }

    /** PUT /progress/title — 얻은 칭호만(null 이면 선택 해제). 탐험가 확인은 트랜잭션 밖에서 먼저 해 갱신 트랜잭션을 짧게 둔다. */
    public ExplorerProgress selectTitle(ExplorerId explorerId, String titleId) {
        territories.personalMapId(explorerId.value());
        return writeTx.execute(status -> {
            ExplorerProgress progress = load(explorerId);
            progress.selectTitle(titleId, catalog.policy(), clock.instant());
            progresses.save(progress);
            return progress;
        });
    }

    /** 체크인 사실(미스터리 지역 모름 — 퀘스트 집계용). */
    static ProgressVisit visitOf(RegionVisited event) {
        return new ProgressVisit(event.mapId(), RegionCode.of(event.regionCode()), event.provinceCode(), event.rarity(),
            event.visitedAt(), event.isFirstClaim(), event.visitGeneration());
    }

    /**
     * 체크인 사실 + 처리 시각이 속한 주의 미스터리 지역(8단계 — 기록이 없는 지난 주는 비어 있어 보너스가 없다). 이벤트 처리와 재계산이 같이 쓴다.
     */
    ProgressVisit visitWithMystery(RegionVisited event) {
        return new ProgressVisit(event.mapId(), RegionCode.of(event.regionCode()), event.provinceCode(), event.rarity(),
            event.visitedAt(), event.isFirstClaim(), event.visitGeneration(), catalog.mysteryOf(event.visitedAt()).orElse(null));
    }

    private ExplorerProgress load(ExplorerId explorerId) {
        return progresses.find(explorerId)
            .orElseGet(() -> ExplorerProgress.start(explorerId, catalog.policy(), clock.instant()));
    }

    /**
     * 이벤트 처리용: 루트 행을 먼저 잠그고 불러온다 — 재계산과 같은 잠금 순서(루트 → 자식)라 교착이 생기지 않는다(구조 QA S2-2).
     */
    private ExplorerProgress loadLocked(ExplorerId explorerId) {
        return progresses.findLocked(explorerId)
            .orElseGet(() -> ExplorerProgress.start(explorerId, catalog.policy(), clock.instant()));
    }

    private void publish(ExplorerProgress progress, ProgressChange change) {
        String explorerId = progress.explorerId().value();
        change.levelUp().ifPresent(level ->
            outbox.append(AGGREGATE, explorerId, new LevelUp(explorerId, level, progress.xp(), change.at())));
        change.badgesEarned().forEach(badge ->
            outbox.append(AGGREGATE, explorerId, new BadgeEarned(explorerId, badge, change.at())));
        change.milestonesReached().forEach(months -> outbox.append(AGGREGATE, explorerId, new StreakMilestoneReached(explorerId,
            months, catalog.policy().streakRules().find(months).orElseThrow().xp(), change.at())));
        change.provincesConquered().forEach(province -> outbox.append(AGGREGATE, explorerId,
            new ProvinceConquered(explorerId, province, catalog.provinceConquest(), change.at())));
        change.mysteryFound().ifPresent(mystery -> outbox.append(AGGREGATE, explorerId, new MysteryBonusEarned(explorerId,
            mystery.weekId(), mystery.region().value(), catalog.mysteryBonus(), change.at())));
    }
}
