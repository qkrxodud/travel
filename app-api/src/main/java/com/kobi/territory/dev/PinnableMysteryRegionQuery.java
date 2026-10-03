package com.kobi.territory.dev;

import com.kobi.territory.catalog.api.query.MysteryRegionQuery;
import com.kobi.territory.catalog.api.query.MysteryWeekView;
import com.kobi.territory.catalog.application.MysteryService;
import com.kobi.territory.catalog.domain.catalog.Catalog;
import com.kobi.territory.catalog.domain.catalog.CatalogRepository;
import com.kobi.territory.catalog.domain.mystery.MysteryWeek;
import com.kobi.territory.catalog.domain.region.Region;
import com.kobi.territory.common.model.RegionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 미스터리 지역 고정(local·E2E 전용 어댑터, 8단계 보완). {@link MysteryRegionQuery} 포트의 운영 어댑터({@link MysteryService} — 주차로
 * 결정적 선택·기록)를 감싸, 고정해 둔 동안은 고정한 주부터 지금 주까지 그 지역을 돌려준다. 고정이 없거나 고정 전 주는 운영 어댑터 그대로.
 * <p>
 * 운영 선택 로직에는 dev 분기가 없다 — 이 빈은 DevController 와 같은 이중 조건(local 프로파일 + territory.dev.enabled=true)에서만
 * 생기고 {@code @Primary} 로 포트 주입을 갈아끼운다(체크인 미리보기·진행 보너스·/mystery/this-week 가 모두 이 포트를 쓴다).
 * 고정은 mystery_week 기록을 바꾸지 않는다(메모리에만 — 서버를 다시 띄우거나 해제·초기화하면 원래 주차 선택으로 돌아간다).
 */
@Profile("local")
@ConditionalOnProperty(prefix = "territory.dev", name = "enabled", havingValue = "true")
@Primary
@Component
public class PinnableMysteryRegionQuery implements MysteryRegionQuery {

    private final MysteryService drawn;
    private final Catalog catalog;
    private final Clock clock;
    private final AtomicReference<MysteryPin> pin = new AtomicReference<>();

    public PinnableMysteryRegionQuery(MysteryService drawn, CatalogRepository catalogs, Clock clock) {
        this.drawn = drawn;
        this.catalog = catalogs.load();
        this.clock = clock;
    }

    @Override
    public Optional<MysteryWeekView> weekOf(Instant at) {
        LocalDate weekStart = MysteryWeek.weekStartOf(at, zone());
        MysteryPin current = pin.get();
        if (current != null && current.covers(weekStart, MysteryWeek.weekStartOf(clock.instant(), zone()))) {
            MysteryWeek pinned = new MysteryWeek(weekStart, current.region(), current.pinnedAt());
            return Optional.of(new MysteryWeekView(pinned.weekStart(), pinned.startsAt(zone()), pinned.endsAt(zone()),
                pinned.region().value(), pinned.selectedAt()));
        }
        return drawn.weekOf(at);
    }

    @Override
    public MysteryWeekView thisWeek() {
        return weekOf(clock.instant()).orElseThrow();
    }

    /**
     * 이번 주부터 미스터리 지역을 region 으로 고정한다(해제 전까지 다음 주들도). 현행 지역이면서 미스터리 규칙의 희귀도(희귀·전설)여야 한다.
     *
     * @throws IllegalArgumentException 없는 지역·폐지된 지역·미스터리가 될 수 없는 희귀도
     */
    public void pin(String regionCode) {
        RegionCode code = RegionCode.of(regionCode);
        Region region = catalog.regions().find(code).filter(Region::active)
            .orElseThrow(() -> new IllegalArgumentException("현행 지역이 아니에요: " + regionCode));
        if (!catalog.mystery().eligible(region.rarity())) {
            throw new IllegalArgumentException("미스터리 지역이 될 수 없는 희귀도예요: " + regionCode + " " + region.rarity());
        }
        Instant now = clock.instant();
        pin.set(new MysteryPin(code, MysteryWeek.weekStartOf(now, zone()), now));
    }

    /** 고정을 풀어 원래 주차 선택으로 돌아간다. */
    public void unpin() {
        pin.set(null);
    }

    /** 고정한 지역(없으면 빈 값). */
    public Optional<RegionCode> pinnedRegion() {
        return Optional.ofNullable(pin.get()).map(MysteryPin::region);
    }

    private ZoneId zone() {
        return clock.getZone();
    }

    /** @param fromWeek 고정한 주(그 주부터 지금 주까지 고정 지역 — 고정 전 주는 원래 기록) */
    private record MysteryPin(RegionCode region, LocalDate fromWeek, Instant pinnedAt) {

        boolean covers(LocalDate weekStart, LocalDate currentWeek) {
            return !weekStart.isBefore(fromWeek) && !weekStart.isAfter(currentWeek);
        }
    }
}
