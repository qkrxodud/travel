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
import com.kobi.territory.exploration.api.event.MemberReassigned;
import com.kobi.territory.progression.api.event.SeasonCompleted;
import com.kobi.territory.progression.api.query.CompletedSeasonView;
import com.kobi.territory.progression.domain.collectionbook.SeasonCalendar;
import com.kobi.territory.progression.domain.collectionbook.SeasonCompletion;
import com.kobi.territory.progression.domain.collectionbook.SeasonProgress;
import com.kobi.territory.progression.domain.collectionbook.SeasonRound;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final Clock clock;

    public CollectionBookService(CollectionBookRepository collectionBooks, ProgressionCatalog catalog,
                                 TerritoryQuery territories, EventOutbox outbox, Clock clock) {
        this.clock = clock;
        this.collectionBooks = collectionBooks;
        this.catalog = catalog;
        this.territories = territories;
        this.outbox = outbox;
    }

    /** 체크인 → 테마 진행 + 그 처리 시각에 열린 계절 회차 진행(9단계). */
    @Transactional
    public void onRegionVisited(RegionVisited event) {
        CollectionBook collectionBook = collectionBooks.load(event.mapId());
        RegionCode region = RegionCode.of(event.regionCode());
        ExplorerId visitor = ExplorerId.of(event.explorerId());
        List<ThemeCompletion> completions = collectionBook.applyVisit(region, visitor, event.visitedAt(), catalog.themes(),
            explorerIds(event.memberIds()));
        List<SeasonCompletion> seasonCompletions = collectionBook.applySeasonVisit(region, visitor, event.visitedAt(),
            catalog.seasonCalendar(), explorerIds(event.memberIds()));
        collectionBooks.save(collectionBook);
        publish(completions);
        publishSeasons(seasonCompletions);
    }

    @Transactional
    public void onVisitCancelled(VisitCancelled event) {
        CollectionBook collectionBook = collectionBooks.load(event.mapId());
        RegionCode region = RegionCode.of(event.regionCode());
        collectionBook.revokeVisit(region, event.regionStillOnMap(), catalog.themes());
        collectionBook.revokeSeasonVisit(region, ExplorerId.of(event.explorerId()), event.cancelledAt(), catalog.seasonCalendar());
        collectionBooks.save(collectionBook);
    }

    /** 탈퇴로 지도에서 사라진 지역을 테마 진행에서 뺀다(완성 기록 유지). 계절 회차는 그 멤버의 숨긴 방문 표시를 뺀다. */
    @Transactional
    public void onVisitsHidden(VisitsHidden event) {
        CollectionBook collectionBook = collectionBooks.load(event.mapId());
        collectionBook.revokeRegions(regionCodes(event.regionsGoneFromMap()), catalog.themes());
        collectionBook.revokeSeasonMember(ExplorerId.of(event.explorerId()), regionCodes(event.hiddenRegionCodes()),
            event.hiddenAt(), catalog.seasonCalendar());
        collectionBooks.save(collectionBook);
    }

    /**
     * 재가입 복구로 지도에 다시 칠해진 지역을 넣는다 — 그로 인한 완성의 수령자는 복구 시점 멤버. 계절 회차는 원래 처리 시각이 기간 안인 복구
     * 방문만 다시 센다.
     */
    @Transactional
    public void onVisitsRestored(VisitsRestored event) {
        CollectionBook collectionBook = collectionBooks.load(event.mapId());
        ExplorerId member = ExplorerId.of(event.explorerId());
        List<ThemeCompletion> completions = collectionBook.restoreRegions(regionCodes(event.regionsBackOnMap()),
            member, event.restoredAt(), catalog.themes(), explorerIds(event.memberIds()));
        Map<RegionCode, Instant> visitedAt = new LinkedHashMap<>();
        event.restoredVisits().forEach(restored -> visitedAt.put(RegionCode.of(restored.regionCode()), restored.visitedAt()));
        List<SeasonCompletion> seasonCompletions = collectionBook.restoreSeasonVisits(visitedAt, member, event.restoredAt(),
            catalog.seasonCalendar(), explorerIds(event.memberIds()));
        collectionBooks.save(collectionBook);
        publish(completions);
        publishSeasons(seasonCompletions);
    }

    /** 병합 재귀속(9단계) — 열린 계절 회차에서 from 이 센 방문을 into 의 것으로(테마 진행은 지도에 칠해진 그대로라 바뀌지 않는다). */
    @Transactional
    public void onMemberReassigned(MemberReassigned event) {
        CollectionBook collectionBook = collectionBooks.load(event.mapId());
        collectionBook.reassignSeasonMember(ExplorerId.of(event.fromExplorerId()), ExplorerId.of(event.intoExplorerId()),
            event.reassignedAt(), catalog.seasonCalendar());
        collectionBooks.save(collectionBook);
    }

    /** GET /seasons/current — mapId 생략 시 개인 지도. 지금 열린 회차·다음 회차·그 지도의 회차 진행(닫힌 회차 기록 포함). */
    @Transactional(readOnly = true)
    public SeasonOverview seasons(ExplorerId explorerId, String mapIdOrNull) {
        String mapId = territories.resolveMapId(explorerId.value(), mapIdOrNull);
        Instant now = clock.instant();
        SeasonCalendar calendar = catalog.seasonCalendar();
        return new SeasonOverview(mapId, now, calendar.roundsOpenAt(now), calendar.nextRoundAfter(now).orElse(null),
            collectionBooks.load(mapId), calendar);
    }

    /**
     * 계절 한정 테마 현황.
     *
     * @param open 지금 열린 회차, @param next 다음에 열리는 회차(없으면 null), @param book 그 지도의 도감(회차 진행 포함)
     */
    public record SeasonOverview(String mapId, Instant now, List<SeasonRound> open, SeasonRound next, CollectionBook book,
                                 SeasonCalendar calendar) {
        public SeasonOverview {
            open = List.copyOf(open);
        }

        /** 이 지도에 기록이 있는 닫힌 회차(최근에 열린 순) — 미완성 기록도 남는다. */
        public List<SeasonRound> closedRounds() {
            return book.seasonProgresses().stream().flatMap(progress -> calendar.round(progress.roundId()).stream())
                .filter(round -> round.endedBy(now)).sorted(Comparator.comparing(SeasonRound::startsAt).reversed()).toList();
        }
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

    @Override
    @Transactional(readOnly = true)
    public List<CompletedSeasonView> completedSeasons(String mapId) {
        return collectionBooks.load(mapId).seasonProgresses().stream().filter(SeasonProgress::completed)
            .map(seasonProgress -> new CompletedSeasonView(seasonProgress.roundId(), seasonProgress.completedAt(),
                seasonProgress.completedMembers().stream().map(ExplorerId::value).sorted().toList()))
            .toList();
    }

    private void publishSeasons(List<SeasonCompletion> completions) {
        completions.forEach(completion -> {
            List<String> recipientIds = completion.recipients().stream().map(ExplorerId::value).toList();
            recipientIds.forEach(recipientId -> outbox.append(AGGREGATE, completion.mapId(), new SeasonCompleted(
                completion.mapId(), completion.roundId(), completion.seasonId(), recipientId, completion.completedAt(),
                completion.completedBy().value(), recipientIds)));
        });
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
