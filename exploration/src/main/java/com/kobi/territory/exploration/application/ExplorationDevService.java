package com.kobi.territory.exploration.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.domain.CheckInPolicy;
import com.kobi.territory.exploration.domain.MapSelector;
import com.kobi.territory.exploration.domain.Territory;
import com.kobi.territory.exploration.domain.TerritoryRepository;
import com.kobi.territory.exploration.domain.Visit;
import java.time.LocalDate;
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

    public ExplorationDevService(CheckInService checkIns, MapAccess mapAccess, TerritoryRepository territories) {
        this.checkIns = checkIns;
        this.mapAccess = mapAccess;
        this.territories = territories;
    }

    /** 개인 지도의 내 방문을 모두 취소한 뒤 샘플 방문을 체크인한다(상한 우회). @return 체크인한 건수 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int seed(ExplorerId explorerId, List<SampleVisit> samples) {
        clear(explorerId);
        for (SampleVisit s : samples) {
            checkIns.checkIn(new CheckInCommand(explorerId, null, s.regionCode(), s.visitDate(), s.memo(), null),
                CheckInPolicy.unlimited());
        }
        return samples.size();
    }

    /** 개인 지도의 내 방문을 모두 취소한다(VisitCancelled 적재). @return 취소한 건수 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int clear(ExplorerId explorerId) {
        var mapId = territories.lockPersonal(explorerId)
            .orElseGet(() -> mapAccess.resolve(explorerId, MapSelector.PERSONAL).map().id());
        Territory territory = territories.load(mapId);
        List<Visit> mine = territory.visitsOf(explorerId);
        mine.forEach(v -> checkIns.cancel(explorerId, null, v.regionCode()));
        return mine.size();
    }

    public record SampleVisit(RegionCode regionCode, LocalDate visitDate, String memo) {

        /** 프로토타입 fillSample 규칙: 오늘 기준 monthOffset 개월 그 달의 day(≤28)일, 미래면 오늘. */
        public static SampleVisit relativeTo(LocalDate today, RegionCode code, int monthOffset, int day, String memo) {
            LocalDate d = java.time.YearMonth.from(today).plusMonths(monthOffset).atDay(Math.min(day, 28));
            return new SampleVisit(code, d.isAfter(today) ? today : d, memo);
        }
    }
}
