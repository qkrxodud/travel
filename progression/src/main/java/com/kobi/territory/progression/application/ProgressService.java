package com.kobi.territory.progression.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.VisitCancelled;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.progression.api.event.BadgeEarned;
import com.kobi.territory.progression.api.event.LevelUp;
import com.kobi.territory.progression.api.event.QuestCompleted;
import com.kobi.territory.progression.api.event.SetCompleted;
import com.kobi.territory.progression.domain.ExplorerProgress;
import com.kobi.territory.progression.domain.ExplorerProgressRepository;
import com.kobi.territory.progression.domain.ProgressChange;
import com.kobi.territory.progression.domain.ProgressVisit;
import com.kobi.territory.progression.domain.QuestPeriod;
import java.time.Clock;
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
        ExplorerProgress progress = load(ExplorerId.of(event.explorerId()));
        ProgressChange change = progress.applyVisit(visitOf(event), catalog.policy());
        progresses.save(progress);
        publish(progress, change);
    }

    @Transactional
    public void onVisitCancelled(VisitCancelled event) {
        ExplorerProgress progress = load(ExplorerId.of(event.explorerId()));
        ProgressChange change = progress.revokeVisit(event.mapId(), RegionCode.of(event.regionCode()), event.cancelledAt(),
            catalog.policy());
        progresses.save(progress);
        publish(progress, change);
    }

    @Transactional
    public void onSetCompleted(SetCompleted event) {
        ExplorerProgress progress = load(ExplorerId.of(event.explorerId()));
        ProgressChange change = progress.applySetCompleted(event.setId(), event.completedAt(), catalog.policy());
        progresses.save(progress);
        publish(progress, change);
    }

    @Transactional
    public void onQuestCompleted(QuestCompleted event) {
        ExplorerProgress progress = load(ExplorerId.of(event.explorerId()));
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

    /** PUT /progress/title — 얻은 칭호만(null 이면 선택 해제). 탐험가 확인은 트랜잭션 밖에서 먼저 해 갱신 트랜잭션을 짧게 둔다. */
    public ExplorerProgress selectTitle(ExplorerId explorerId, String titleId) {
        territories.personalMapId(explorerId.value());
        return writeTx.execute(status -> {
            ExplorerProgress progress = load(explorerId);
            progress.selectTitle(titleId, catalog.policy());
            progresses.save(progress);
            return progress;
        });
    }

    static ProgressVisit visitOf(RegionVisited event) {
        return new ProgressVisit(event.mapId(), RegionCode.of(event.regionCode()), event.provinceCode(), event.rarity(),
            event.visitedAt(), event.isFirstClaim());
    }

    private ExplorerProgress load(ExplorerId explorerId) {
        return progresses.find(explorerId)
            .orElseGet(() -> ExplorerProgress.start(explorerId, catalog.policy(), clock.instant()));
    }

    private void publish(ExplorerProgress progress, ProgressChange change) {
        String explorerId = progress.explorerId().value();
        change.levelUp().ifPresent(level ->
            outbox.append(AGGREGATE, explorerId, new LevelUp(explorerId, level, progress.xp(), change.at())));
        change.badgesEarned().forEach(badge ->
            outbox.append(AGGREGATE, explorerId, new BadgeEarned(explorerId, badge, change.at())));
    }
}
