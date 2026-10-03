package com.kobi.territory.sharing.application;

import com.kobi.territory.catalog.api.query.ProgressionRules;
import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.progression.api.query.CollectionBookQuery;
import com.kobi.territory.progression.api.query.ProgressQuery;
import com.kobi.territory.progression.api.query.ProgressSummaryView;
import com.kobi.territory.sharing.domain.showcase.ProvinceInfo;
import com.kobi.territory.sharing.domain.showcase.PublicVisits;
import com.kobi.territory.sharing.domain.showcase.RegionAtlas;
import com.kobi.territory.sharing.domain.showcase.RegionInfo;
import com.kobi.territory.sharing.domain.showcase.Showcase;
import com.kobi.territory.sharing.domain.showcase.ShowcaseProgress;
import com.kobi.territory.sharing.domain.showcase.ShowcaseScene;
import com.kobi.territory.sharing.domain.showcase.VisitFact;
import com.kobi.territory.wardrobe.api.query.SceneQuery;
import com.kobi.territory.wardrobe.api.query.SceneSummaryView;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 상류 공개 Query(탐험·카탈로그·진행·꾸미기) → 공유 도메인의 공개 정보(Showcase) 변환 어댑터(Anti-Corruption Layer).
 * 탐험의 방문 이력은 처음부터 메모·사진을 싣지 않는다 — 여기서도 지역 코드·방문일·처리 시각만 옮긴다.
 * 카드·공개 프로필은 그 탐험가의 개인 지도 기준이다(4단계 범위).
 */
@Component
public class ShowcaseReader {

    private final TerritoryQuery territories;
    private final ProgressionRules progressionRules;
    private final ProgressQuery progresses;
    private final CollectionBookQuery collectionBooks;
    private final SceneQuery scenes;
    private final RegionAtlas atlas;

    public ShowcaseReader(TerritoryQuery territories, RegionCatalog regions, ProgressionRules progressionRules,
                          ProgressQuery progresses, CollectionBookQuery collectionBooks, SceneQuery scenes) {
        this.territories = territories;
        this.progressionRules = progressionRules;
        this.progresses = progresses;
        this.collectionBooks = collectionBooks;
        this.scenes = scenes;
        this.atlas = loadAtlas(regions);
    }

    /** 지역 정의는 배포 단위로만 바뀌는 참조 데이터라 시작 시 한 번 만든다. */
    private static RegionAtlas loadAtlas(RegionCatalog regions) {
        Map<String, String> provinceNames = regions.provinces().stream()
            .collect(Collectors.toMap(province -> province.code(), province -> province.name()));
        List<RegionInfo> infos = regions.activeRegions().stream()
            .map(region -> new RegionInfo(region.code(), region.name(), region.provinceCode(),
                provinceNames.getOrDefault(region.provinceCode(), region.provinceName()), region.rarity()))
            .toList();
        List<ProvinceInfo> provinces = regions.provinces().stream()
            .map(province -> new ProvinceInfo(province.code(), province.name(), province.regionCount())).toList();
        return RegionAtlas.of(infos, provinces);
    }

    /** @param handle 공개 handle(익명 탐험가의 내 카드 미리보기면 null) */
    public Showcase read(String explorerId, String handle) {
        return new Showcase(handle, atlas, visitsOf(explorerId), progressOf(explorerId), sceneOf(explorerId));
    }

    private PublicVisits visitsOf(String explorerId) {
        String personalMapId = territories.personalMapId(explorerId);
        List<VisitFact> facts = territories.visitHistory(personalMapId).stream()
            .filter(visit -> visit.explorerId().equals(explorerId))
            .map(visit -> new VisitFact(visit.regionCode(), visit.visitDate(), visit.visitedAt()))
            .toList();
        return PublicVisits.of(facts, atlas);
    }

    private ShowcaseProgress progressOf(String explorerId) {
        ProgressSummaryView progress = progresses.summaryOf(explorerId);
        int themesCompleted = collectionBooks.completedSetIds(territories.personalMapId(explorerId)).size();
        return new ShowcaseProgress(progress.level(), progress.titleName(), progress.streakMonths(), themesCompleted,
            progressionRules.sets().size());
    }

    private ShowcaseScene sceneOf(String explorerId) {
        SceneSummaryView scene = scenes.summaryOf(explorerId);
        return new ShowcaseScene(scene.stylePoints(),
            scene.wornItems().stream().map(SceneSummaryView.WornItemView::name).toList(), scene.ownedCount());
    }
}
