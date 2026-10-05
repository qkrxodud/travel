package com.kobi.territory.catalog.infra.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.catalog.domain.catalog.Catalog;
import com.kobi.territory.catalog.domain.definition.SeasonDefinition;
import com.kobi.territory.catalog.domain.lineup.FestivalFetch;
import com.kobi.territory.catalog.domain.lineup.LineupPolicy;
import com.kobi.territory.catalog.domain.lineup.LineupProvenance;
import com.kobi.territory.catalog.domain.lineup.LineupRegion;
import com.kobi.territory.catalog.domain.lineup.LineupSelection;
import com.kobi.territory.catalog.domain.lineup.LineupSelector;
import com.kobi.territory.catalog.infra.repository.JsonCatalogRepository;
import com.kobi.territory.common.model.RegionCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실제 TourAPI 응답으로 고르기 — 2026-10-05 에 실제 키로 한 번 녹화한 2026 봄(3/6~5/14) 행사 198건(본문만, 열쇠 없음). 벚꽃 축제가 스물두 건이라
 * 근거 지역 열 곳을 모두 채운다.
 */
@DisplayName("실제 TourAPI 응답으로 봄 회차 고르기")
class RealSpringResponseLineupTest {

    @Test
    @DisplayName("2026 봄 실제 행사로 고르면 열 곳 모두 TourAPI 근거가 있고, 오래 여는 벚꽃 축제 지역이 든다")
    void realSpring2026() throws IOException {
        Catalog catalog = new JsonCatalogRepository().load();
        SeasonDefinition spring = catalog.progression().seasons().stream().filter(season -> season.id().equals("spring")).findFirst()
            .orElseThrow();
        String body;
        try (InputStream in = getClass().getResourceAsStream("/tourapi/searchFestival2-real-2026-spring.json")) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        TourApiResponses.Page page = (TourApiResponses.Page) TourApiResponses.parse(body, new ObjectMapper());
        LineupSelection selection = new LineupSelector(spring, catalog.regionLocator(3), new LineupPolicy(10, 14, 5))
            .select(spring.windowOf(2026, ZoneId.of("Asia/Seoul")),
                new FestivalFetch.Fetched(page.festivals(), Instant.parse("2026-10-05T04:00:00Z"), false));

        assertThat(page.festivals()).hasSize(198);
        assertThat(selection.themedFestivals()).isEqualTo(22);
        assertThat(selection.unlocatedFestivals()).isZero();
        assertThat(selection.regions().stream()).hasSize(10).allMatch(region -> region.provenance() == LineupProvenance.TOURAPI);
        assertThat(selection.regions().stream().map(LineupRegion::code))
            .contains(RegionCode.of("KR-35020"), RegionCode.of("KR-33030"), RegionCode.of("KR-11240"));
    }
}
