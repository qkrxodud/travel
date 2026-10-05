package com.kobi.territory.catalog.domain.lineup;

import com.kobi.territory.catalog.domain.definition.SeasonDefinition;
import com.kobi.territory.catalog.domain.definition.SeasonRoundWindow;
import com.kobi.territory.catalog.domain.region.RegionLocator;
import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 도메인 서비스: 축제·관광지 자료로 계절 회차 후보를 고른다(13s단계).
 * <ol>
 *   <li>같은 콘텐츠 id 는 한 번만 센다</li>
 *   <li>축제는 여유를 둔 회차 기간과 겹치고 이름이 계절 테마 키워드에 맞는 것만. 관광지는 계절 키워드 검색 결과 그대로(기간 없음)</li>
 *   <li>위치를 우리 시·군·구로 옮긴다({@link RegionLocator} — 좌표 → 경계, 없으면 주소). 못 찾으면 뺀다</li>
 *   <li>지역 순위: 축제 근거 우선 — 축제 수가 많은 순 → 축제 일수 합(규모)이 큰 순 → 그다음 관광지 근거 수 → 지역 코드 순(결정적)</li>
 *   <li>상위 {@code size} 곳. 모자라면 기본 목록(AI 추정)에서 아직 없는 지역을 정의 순서대로 채우고 출처를 구분한다</li>
 * </ol>
 */
public final class LineupSelector {

    private static final Comparator<RegionTally> RANKING = Comparator.comparingInt(RegionTally::festivalCount).reversed()
        .thenComparing(Comparator.comparingLong(RegionTally::totalDays).reversed())
        .thenComparing(Comparator.comparingInt(RegionTally::attractionCount).reversed())
        .thenComparing(tally -> tally.code().value());

    private final SeasonDefinition season;
    private final RegionLocator locator;
    private final LineupPolicy policy;

    public LineupSelector(SeasonDefinition season, RegionLocator locator, LineupPolicy policy) {
        this.season = Objects.requireNonNull(season, "season");
        this.locator = Objects.requireNonNull(locator, "locator");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public LineupSelection select(SeasonRoundWindow window, FestivalFetch.Fetched fetched) {
        Map<String, Festival> unique = new LinkedHashMap<>();
        fetched.festivals().forEach(festival -> unique.putIfAbsent(festival.contentId(), festival));
        List<Festival> themed = unique.values().stream()
            .filter(festival -> season.themeMatches(festival.title()))
            .filter(festival -> window.overlapsSearchRange(festival.startDate(), festival.endDate(), policy.marginDays()))
            .toList();
        Map<String, Attraction> attractions = new LinkedHashMap<>();
        fetched.attractions().forEach(attraction -> attractions.putIfAbsent(attraction.contentId(), attraction));
        Map<RegionCode, RegionTally> tallies = new LinkedHashMap<>();
        int unlocated = 0;
        for (Festival festival : themed) {
            Optional<RegionCode> region = locator.locate(festival.location(), festival.address()).map(match -> match.code());
            if (region.isEmpty()) {
                unlocated++;
                continue;
            }
            tallies.computeIfAbsent(region.get(), RegionTally::new).add(festival);
        }
        for (Attraction attraction : attractions.values()) {
            Optional<RegionCode> region = locator.locate(attraction.location(), attraction.address()).map(match -> match.code());
            if (region.isEmpty()) {
                unlocated++;
                continue;
            }
            tallies.computeIfAbsent(region.get(), RegionTally::new).add(attraction);
        }
        List<LineupRegion> chosen = new ArrayList<>(tallies.values().stream().sorted(RANKING).limit(policy.size())
            .map(tally -> new LineupRegion(tally.code(), LineupProvenance.TOURAPI,
                tally.evidence(policy.evidencePerRegion(), fetched.fetchedAt())))
            .toList());
        int evidenced = chosen.size();
        season.regions().stream()
            .filter(code -> chosen.stream().noneMatch(region -> region.code().equals(code)))
            .limit(Math.max(0, policy.size() - evidenced))
            .forEach(code -> chosen.add(LineupRegion.aiEstimate(code)));
        return new LineupSelection(LineupRegions.of(chosen), themed.size(), unlocated,
            warnings(themed.size() + attractions.size(), evidenced, chosen.size() - evidenced, unlocated, fetched.truncated()));
    }

    private List<String> warnings(int sources, int evidenced, int filled, int unlocated, boolean truncated) {
        List<String> warnings = new ArrayList<>();
        if (sources == 0) {
            warnings.add("기간 안에 계절 테마(" + String.join("·", season.keywords()) + ")에 맞는 축제·관광지가 없어 AI 추정 목록을 그대로 씁니다");
        } else if (evidenced < policy.size()) {
            warnings.add("TourAPI 근거가 있는 지역이 " + evidenced + "곳이라 나머지 " + filled + "곳은 AI 추정 목록으로 채웠습니다");
        }
        if (unlocated > 0) warnings.add("우리 지역을 찾지 못한 축제·관광지 " + unlocated + "건(좌표·주소 없음 또는 경계 밖)은 뺐습니다");
        if (truncated) warnings.add("축제가 많아 앞쪽 일부만 읽었습니다(쪽 수 상한) — 순위가 달라질 수 있습니다");
        return warnings;
    }

    /** 지역 하나의 축제·관광지 집계(순위 계산용). */
    private static final class RegionTally {

        private final RegionCode code;
        private final List<Festival> festivals = new ArrayList<>();
        private final List<Attraction> attractions = new ArrayList<>();

        private RegionTally(RegionCode code) {
            this.code = code;
        }

        void add(Festival festival) {
            festivals.add(festival);
        }

        void add(Attraction attraction) {
            attractions.add(attraction);
        }

        int attractionCount() {
            return attractions.size();
        }

        RegionCode code() {
            return code;
        }

        int festivalCount() {
            return festivals.size();
        }

        long totalDays() {
            return festivals.stream().mapToLong(Festival::days).sum();
        }

        /** 축제(이른 순) 먼저, 그다음 관광지(이름 순) — 합쳐서 limit 건까지. */
        List<LineupEvidence> evidence(int limit, Instant fetchedAt) {
            List<LineupEvidence> evidence = new ArrayList<>(festivals.stream()
                .sorted(Comparator.comparing(Festival::startDate).thenComparing(Festival::contentId))
                .map(festival -> festival.evidence(fetchedAt)).toList());
            attractions.stream().sorted(Comparator.comparing(Attraction::title).thenComparing(Attraction::contentId))
                .forEach(attraction -> evidence.add(attraction.evidence(fetchedAt)));
            return List.copyOf(evidence.subList(0, Math.min(limit, evidence.size())));
        }
    }
}
