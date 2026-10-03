package com.kobi.territory.catalog.application;

import com.kobi.territory.catalog.api.query.MysteryRegionQuery;
import com.kobi.territory.catalog.api.query.MysteryWeekView;
import com.kobi.territory.catalog.domain.catalog.Catalog;
import com.kobi.territory.catalog.domain.catalog.CatalogRepository;
import com.kobi.territory.catalog.domain.mystery.MysteryDraw;
import com.kobi.territory.catalog.domain.mystery.MysterySeed;
import com.kobi.territory.catalog.domain.mystery.MysteryWeek;
import com.kobi.territory.catalog.domain.mystery.MysteryWeekRepository;
import com.kobi.territory.catalog.domain.mystery.RegionVisitors;
import com.kobi.territory.catalog.domain.mystery.VisitorShares;
import com.kobi.territory.common.model.RegionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이번 주 미스터리 지역({@link MysteryRegionQuery} 구현, 8단계). 고르기는 도메인(MysteryDraw), 여기는 기록 읽기·쓰기 순서만 둔다.
 * <p>
 * 기록 읽기·쓰기는 호출자 트랜잭션과 <b>따로</b>(REQUIRES_NEW) 한다 — 체크인·진행 이벤트 처리 중에 불려도 그 트랜잭션의 스냅숏·잠금에
 * 묶이지 않고, 같은 주를 동시에 고르면 한쪽이 유일성 위반으로 실패한 뒤 새 트랜잭션에서 먼저 기록된 것을 읽는다(누구에게나 같은 지역).
 * 기록은 바뀌지 않으므로 한 번 읽은 주는 메모리에 둔다.
 */
@Service
public class MysteryService implements MysteryRegionQuery {

    private final MysteryWeekRepository weeks;
    private final Catalog catalog;
    private final ObjectProvider<RegionVisitorCounts> visitorCounts;
    private final MysterySeed seed;
    private final Clock clock;
    private final TransactionTemplate separateTx;
    private final Map<LocalDate, MysteryWeek> recorded = new ConcurrentHashMap<>();
    /** 기록이 없다고 확인한 지난 주(다시 고르지 않으므로 계속 없다 — QA P3-7: 재계산·예전 이벤트마다 다시 묻지 않게). */
    private final Set<LocalDate> missingPast = ConcurrentHashMap.newKeySet();

    public MysteryService(MysteryWeekRepository weeks, CatalogRepository catalogs, ObjectProvider<RegionVisitorCounts> visitorCounts,
                          MysterySettings settings, Clock clock, PlatformTransactionManager transactionManager) {
        this.weeks = weeks;
        this.seed = settings.seed();
        this.catalog = catalogs.load();
        this.visitorCounts = visitorCounts;
        this.clock = clock;
        this.separateTx = new TransactionTemplate(transactionManager);
        this.separateTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Optional<MysteryWeekView> weekOf(Instant at) {
        LocalDate weekStart = MysteryWeek.weekStartOf(at, zone());
        Optional<MysteryWeek> week = recordedWeek(weekStart);
        if (week.isEmpty() && MysteryWeek.drawableAt(weekStart, clock.instant(), zone())) {
            week = Optional.of(select(weekStart));
        }
        return week.map(this::toView);
    }

    @Override
    public MysteryWeekView thisWeek() {
        return weekOf(clock.instant()).orElseThrow();
    }

    private Optional<MysteryWeek> recordedWeek(LocalDate weekStart) {
        MysteryWeek cached = recorded.get(weekStart);
        if (cached != null) return Optional.of(cached);
        boolean past = MysteryWeek.pastAt(weekStart, clock.instant(), zone());
        if (past && missingPast.contains(weekStart)) return Optional.empty();
        Optional<MysteryWeek> found = separateTx.execute(status -> weeks.find(weekStart));
        found.ifPresentOrElse(week -> recorded.put(weekStart, week), () -> {
            if (past) missingPast.add(weekStart);
        });
        return found;
    }

    /** 지금 주를 골라 기록한다. 동시에 다른 요청이 먼저 기록했으면(유일성 위반) 그 기록을 쓴다. */
    private MysteryWeek select(LocalDate weekStart) {
        MysteryWeek drawn = MysteryDraw.draw(weekStart, catalog.regions().active(), shares(), catalog.mystery(), seed,
            clock.instant());
        try {
            separateTx.executeWithoutResult(status -> weeks.add(drawn));
            recorded.put(weekStart, drawn);
            return drawn;
        } catch (DataIntegrityViolationException alreadyRecorded) {
            return recordedWeek(weekStart).orElseThrow(() -> alreadyRecorded);
        }
    }

    private VisitorShares shares() {
        RegionVisitorCounts counts = visitorCounts.getIfAvailable();
        if (counts == null) return VisitorShares.none();
        return VisitorShares.of(counts.counts().stream()
            .map(count -> new RegionVisitors(RegionCode.of(count.regionCode()), count.visitors(), count.population())).toList());
    }

    private MysteryWeekView toView(MysteryWeek week) {
        return new MysteryWeekView(week.weekStart(), week.startsAt(zone()), week.endsAt(zone()), week.region().value(),
            week.selectedAt());
    }

    private ZoneId zone() {
        return clock.getZone();
    }
}
