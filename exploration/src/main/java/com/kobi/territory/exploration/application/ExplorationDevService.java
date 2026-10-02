package com.kobi.territory.exploration.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.map.CheckInPolicy;
import com.kobi.territory.exploration.domain.map.MapId;
import com.kobi.territory.exploration.domain.map.MapSelector;
import com.kobi.territory.exploration.domain.territory.Territory;
import com.kobi.territory.exploration.domain.territory.TerritoryRepository;
import com.kobi.territory.exploration.domain.territory.Visit;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 개발·E2E 전용 유스케이스(local 프로파일에서만 빈 생성). 일반 체크인·취소 경로를 그대로 타서
 * 이벤트도 정상 적재된다. 시드만 하루 상한을 우회한다.
 */
@Service
@Profile("local")
public class ExplorationDevService {

    private final CheckInService checkIns;
    private final MapAccess mapAccess;
    private final TerritoryRepository territories;
    private final Clock clock;

    public ExplorationDevService(CheckInService checkIns, MapAccess mapAccess, TerritoryRepository territories, Clock clock) {
        this.checkIns = checkIns;
        this.mapAccess = mapAccess;
        this.territories = territories;
        this.clock = clock;
    }

    /**
     * 개인 지도의 내 방문을 모두 취소한 뒤 샘플 방문을 주어진 순서대로 체크인한다(상한 우회).
     * 처리 시각(visitedAt)은 샘플 방문일 정오(+순번 초, 지금보다 늦지 않게)로 둔다(D6). 스트릭이 처리 시각 순서로
     * 쌓이므로 호출자는 샘플을 방문일 순으로 넘긴다. @return 체크인한 건수
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int seed(ExplorerId explorerId, List<SampleVisit> samples) {
        clear(explorerId);
        Instant now = clock.instant();
        for (int i = 0; i < samples.size(); i++) {
            SampleVisit sample = samples.get(i);
            checkIns.checkIn(new CheckInCommand(explorerId, null, sample.regionCode(), sample.visitDate(), sample.memo(), null),
                CheckInPolicy.unlimited(), sample.visitedAt(clock.getZone(), i, now));
        }
        return samples.size();
    }

    /** 개인 지도의 내 방문을 모두 취소한다(VisitCancelled 적재). @return 취소한 건수 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int clear(ExplorerId explorerId) {
        MapId mapId = territories.lockPersonal(explorerId)
            .orElseGet(() -> mapAccess.resolve(explorerId, MapSelector.PERSONAL).map().id());
        Territory territory = territories.load(mapId);
        List<Visit> mine = territory.visitsOf(explorerId);
        mine.forEach(visit -> checkIns.cancel(explorerId, null, visit.regionCode()));
        return mine.size();
    }

    public record SampleVisit(RegionCode regionCode, LocalDate visitDate, String memo) {

        /** 프로토타입 fillSample 규칙: 오늘 기준 monthOffset 개월 그 달의 day(≤28)일, 미래면 오늘. */
        public static SampleVisit relativeTo(LocalDate today, RegionCode code, int monthOffset, int day, String memo) {
            LocalDate date = java.time.YearMonth.from(today).plusMonths(monthOffset).atDay(Math.min(day, 28));
            return new SampleVisit(code, date.isAfter(today) ? today : date, memo);
        }

        /** 시드용 처리 시각: 방문일 정오 + order 초, 단 now 이후로는 잡지 않는다. */
        Instant visitedAt(ZoneId zone, int order, Instant now) {
            Instant at = visitDate.atTime(LocalTime.NOON).atZone(zone).toInstant().plusSeconds(order);
            return at.isAfter(now) ? now : at;
        }
    }
}
