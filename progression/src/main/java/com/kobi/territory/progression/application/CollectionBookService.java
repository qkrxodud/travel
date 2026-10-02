package com.kobi.territory.progression.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.VisitCancelled;
import com.kobi.territory.exploration.api.event.VisitsHidden;
import com.kobi.territory.exploration.api.event.VisitsRestored;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.progression.api.event.SetCompleted;
import com.kobi.territory.progression.api.query.CollectionBookQuery;
import com.kobi.territory.progression.api.query.CompletedSetView;
import com.kobi.territory.progression.domain.collectionbook.CollectionBook;
import com.kobi.territory.progression.domain.collectionbook.CollectionBookRepository;
import com.kobi.territory.progression.domain.collectionbook.ThemeCompletion;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도감(CollectionBook, 지도 단위) 유스케이스. 진행·완성 판정은 CollectionBook·Themes 가 한다. 공개 이벤트는 이름 그대로
 * SetCompleted(setId = 테마 id) — 완성 시점 멤버 전원이 수령자이고 수령자마다 한 건씩 낸다(결정 1).
 */
@Service
public class CollectionBookService implements CollectionBookQuery {

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
            ExplorerId.of(event.explorerId()), event.visitedAt(), catalog.themes(), explorerIds(event.memberIds()));
        collectionBooks.save(collectionBook);
        publish(completions);
    }

    @Transactional
    public void onVisitCancelled(VisitCancelled event) {
        CollectionBook collectionBook = collectionBooks.load(event.mapId());
        collectionBook.revokeVisit(RegionCode.of(event.regionCode()), event.regionStillOnMap(), catalog.themes());
        collectionBooks.save(collectionBook);
    }

    /** 탈퇴로 지도에서 사라진 지역을 테마 진행에서 뺀다(완성 기록 유지). */
    @Transactional
    public void onVisitsHidden(VisitsHidden event) {
        CollectionBook collectionBook = collectionBooks.load(event.mapId());
        collectionBook.revokeRegions(regionCodes(event.regionsGoneFromMap()), catalog.themes());
        collectionBooks.save(collectionBook);
    }

    /** 재가입 복구로 지도에 다시 칠해진 지역을 넣는다 — 그로 인한 완성의 수령자는 복구 시점 멤버. */
    @Transactional
    public void onVisitsRestored(VisitsRestored event) {
        CollectionBook collectionBook = collectionBooks.load(event.mapId());
        List<ThemeCompletion> completions = collectionBook.restoreRegions(regionCodes(event.regionsBackOnMap()),
            ExplorerId.of(event.explorerId()), event.restoredAt(), catalog.themes(), explorerIds(event.memberIds()));
        collectionBooks.save(collectionBook);
        publish(completions);
    }

    /** GET /collection — mapId 생략 시 개인 지도. 멤버가 아니면 403(탐험 Query 가 판단). */
    @Transactional(readOnly = true)
    public CollectionBook view(ExplorerId explorerId, String mapIdOrNull) {
        return collectionBooks.load(territories.resolveMapId(explorerId.value(), mapIdOrNull));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> completedSetIds(String mapId) {
        return collectionBooks.load(mapId).completedThemeIds();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompletedSetView> completedSets(String mapId) {
        CollectionBook collectionBook = collectionBooks.load(mapId);
        return collectionBook.completedThemeIds().stream()
            .map(themeId -> new CompletedSetView(themeId, collectionBook.progressOf(themeId).completedAt())).toList();
    }

    private void publish(List<ThemeCompletion> completions) {
        completions.forEach(completion -> {
            List<String> recipientIds = completion.recipients().stream().map(ExplorerId::value).toList();
            recipientIds.forEach(recipientId -> outbox.append(AGGREGATE, completion.mapId(), new SetCompleted(
                completion.mapId(), completion.themeId(), recipientId, completion.completedAt(),
                completion.completedBy().value(), recipientIds)));
        });
    }

    private static List<ExplorerId> explorerIds(List<String> ids) {
        return ids == null ? List.of() : ids.stream().map(ExplorerId::of).toList();
    }

    private static List<RegionCode> regionCodes(List<String> codes) {
        return codes.stream().map(RegionCode::of).toList();
    }
}
