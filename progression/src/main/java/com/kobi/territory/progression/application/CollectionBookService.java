package com.kobi.territory.progression.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.VisitCancelled;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.progression.api.event.SetCompleted;
import com.kobi.territory.progression.domain.collectionbook.CollectionBook;
import com.kobi.territory.progression.domain.collectionbook.CollectionBookRepository;
import com.kobi.territory.progression.domain.collectionbook.ThemeCompletion;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 도감(CollectionBook, 지도 단위) 유스케이스. 진행·완성 판정은 CollectionBook·Themes 가 한다. 공개 이벤트는 이름 그대로 SetCompleted(setId = 테마 id). */
@Service
public class CollectionBookService {

    static final String AGGREGATE = "Collection";

    private final CollectionBookRepository collectionBooks;
    private final ProgressionCatalog catalog;
    private final TerritoryQuery territories;
    private final EventOutbox outbox;

    public CollectionBookService(CollectionBookRepository collectionBooks, ProgressionCatalog catalog,
                                 TerritoryQuery territories, EventOutbox outbox) {
        this.collectionBooks = collectionBooks;
        this.catalog = catalog;
        this.territories = territories;
        this.outbox = outbox;
    }

    @Transactional
    public void onRegionVisited(RegionVisited event) {
        CollectionBook collectionBook = collectionBooks.load(event.mapId());
        List<ThemeCompletion> completions = collectionBook.applyVisit(RegionCode.of(event.regionCode()),
            ExplorerId.of(event.explorerId()), event.visitedAt(), catalog.themes());
        collectionBooks.save(collectionBook);
        completions.forEach(completion -> outbox.append(AGGREGATE, completion.mapId(), new SetCompleted(
            completion.mapId(), completion.themeId(), completion.completedBy().value(), completion.completedAt())));
    }

    @Transactional
    public void onVisitCancelled(VisitCancelled event) {
        CollectionBook collectionBook = collectionBooks.load(event.mapId());
        collectionBook.revokeVisit(RegionCode.of(event.regionCode()), event.regionStillOnMap(), catalog.themes());
        collectionBooks.save(collectionBook);
    }

    /** GET /collection — mapId 생략 시 개인 지도. 멤버가 아니면 403(탐험 Query 가 판단). */
    @Transactional(readOnly = true)
    public CollectionBook view(ExplorerId explorerId, String mapIdOrNull) {
        return collectionBooks.load(territories.resolveMapId(explorerId.value(), mapIdOrNull));
    }
}
