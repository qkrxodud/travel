package com.kobi.territory.sharing.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.progression.api.query.CollectionBookQuery;
import com.kobi.territory.sharing.domain.showcase.YearRecap;
import java.time.Clock;
import java.time.Year;
import org.springframework.stereotype.Service;

/**
 * 내 연간 리캡 JSON 유스케이스(06 QA P2-1) — 화면이 리캡 공식을 복제하지 않고 이 값을 그대로 보여 준다. 계산은 카드 PNG 와 같은
 * {@code PublicVisits.recap}(중복 구현 없음). 본인 전용(로그인 세션 또는 익명 토큰)이고 공개 범위와 무관하다. 메모·사진은 처음부터
 * 들고 오지 않는다(ShowcaseReader).
 */
@Service
public class RecapService {

    private final TerritoryQuery territories;
    private final ShowcaseReader showcases;
    private final CollectionBookQuery collectionBooks;
    private final Clock clock;

    public RecapService(TerritoryQuery territories, ShowcaseReader showcases, CollectionBookQuery collectionBooks, Clock clock) {
        this.territories = territories;
        this.showcases = showcases;
        this.collectionBooks = collectionBooks;
        this.clock = clock;
    }

    /**
     * @param yearOrNull  연도(생략이면 올해, 범위 밖이면 400 INVALID_YEAR)
     * @param mapIdOrNull 지도(생략이면 개인 지도). 탐험가 없음 404 EXPLORER_NOT_FOUND, 지도 없음 404 MAP_NOT_FOUND, 멤버 아님 403 NOT_A_MEMBER
     */
    public MyRecap recapOf(ExplorerId explorerId, Integer yearOrNull, String mapIdOrNull) {
        Year year = YearRecap.yearOf(yearOrNull, Year.now(clock));
        String mapId = territories.resolveMapId(explorerId.value(), mapIdOrNull);
        YearRecap recap = showcases.visitsOn(explorerId.value(), mapId).recap(year);
        return new MyRecap(mapId, recap, collectionBooks.completedSetIds(mapId).size());
    }
}
