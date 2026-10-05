package com.kobi.territory.catalog.domain.lineup;

import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.관광지;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.먼바다;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.영월;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.벚꽃_열두_지역;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.봄;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.봄2027;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.봄_고르기;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.영등포;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.위치찾기;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.읽음;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.조회시각;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.지역;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.진해;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.축제;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.하동;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.경주;
import static com.kobi.territory.catalog.domain.lineup.LineupFixtures.송파;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.kobi.territory.common.model.RegionCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 계절 회차 후보 고르기: 회차 기간(앞뒤 여유)에 열리는 계절 테마 축제를 우리 시·군·구로 옮겨, 축제가 많은(그다음 오래 여는) 지역 열 곳.
 * 모자라면 기본 목록(AI 추정)으로 채우고 출처를 가른다. 봄(벚꽃) 2027 회차, 여유 14일.
 */
@DisplayName("계절 회차 후보 고르기")
class LineupSelectorTest {

    private static LineupSelection 고른다(List<Festival> festivals) {
        return 봄_고르기().select(봄2027, 읽음(festivals));
    }

    private static LineupSelection 고른다(List<Festival> festivals, List<Attraction> attractions) {
        return 봄_고르기().select(봄2027, LineupFixtures.읽음(festivals, attractions));
    }

    private static List<RegionCode> 근거_지역(LineupSelection selection) {
        return selection.regions().stream().filter(region -> region.provenance() == LineupProvenance.TOURAPI).map(LineupRegion::code)
            .toList();
    }

    @Nested
    @DisplayName("축제가 충분하면")
    class Enough {

        @Test
        @DisplayName("축제 수가 많은 지역부터, 같으면 오래 여는 지역부터, 그래도 같으면 지역 코드 순으로 열 곳을 고른다")
        void ranking() {
            LineupSelection selection = 고른다(벚꽃_열두_지역());

            assertThat(selection.regions().codes()).containsExactly(지역("KR-38115"), 지역("KR-37020"), 지역("KR-11190"), 지역("KR-11240"),
                지역("KR-32030"), 지역("KR-38360"), 지역("KR-32010"), 지역("KR-33020"), 지역("KR-36330"), 지역("KR-39010"));
        }

        @Test
        @DisplayName("열 곳 모두 근거가 있는 TourAPI 출처이고 경고가 없다")
        void allEvidenced() {
            LineupSelection selection = 고른다(벚꽃_열두_지역());

            assertThat(selection.evidencedRegions()).isEqualTo(10);
            assertThat(selection.regions().provenance()).isEqualTo("tourapi");
            assertThat(selection.warnings()).isEmpty();
        }

        @Test
        @DisplayName("열 곳 밖으로 밀린 지역(축제 이틀짜리 울산 남구·군산)은 들지 않는다")
        void cutOff() {
            assertThat(고른다(벚꽃_열두_지역()).regions().codes()).doesNotContain(지역("KR-26020"), 지역("KR-35020"));
        }

        @Test
        @DisplayName("좌표가 없는 축제는 주소로 지역을 찾는다")
        void byAddress() {
            assertThat(근거_지역(고른다(벚꽃_열두_지역()))).contains(지역("KR-33020"));
        }
    }

    @Nested
    @DisplayName("근거는")
    class Evidence {

        @Test
        @DisplayName("지역마다 그 지역 축제의 이름·기간·콘텐츠 id·조회 시각을 이른 순으로 남긴다")
        void perRegion() {
            LineupRegion 진해구 = 고른다(벚꽃_열두_지역()).regions().stream().filter(region -> region.code().equals(지역("KR-38115")))
                .findFirst().orElseThrow();

            assertThat(진해구.evidence()).extracting(LineupEvidence::contentId, LineupEvidence::title)
                .containsExactly(tuple("1", "진해 벚꽃 축제"), tuple("2", "진해 벚꽃 야행"));
            assertThat(진해구.evidence()).allMatch(item -> item.fetchedAt().equals(조회시각));
        }

        @Test
        @DisplayName("한 지역의 근거는 정책의 상한까지만 남긴다")
        void limited() {
            List<Festival> festivals = new ArrayList<>();
            for (int i = 0; i < 7; i++) festivals.add(축제("f" + i, "진해 벚꽃 " + i, "04-0" + (i + 1), "04-0" + (i + 1), 진해));
            LineupSelection selection = new LineupSelector(봄, 위치찾기, new LineupPolicy(10, 14, 3)).select(봄2027, 읽음(festivals));

            assertThat(selection.regions().stream().findFirst().orElseThrow().evidence()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("세지 않는 축제")
    class Excluded {

        @Test
        @DisplayName("이름이 계절 테마에 맞지 않으면 세지 않는다")
        void offTheme() {
            LineupSelection selection = 고른다(List.of(축제("1", "봄 딸기 축제", "04-01", "04-03", 영등포)));

            assertThat(selection.themedFestivals()).isZero();
            assertThat(selection.evidencedRegions()).isZero();
        }

        @Test
        @DisplayName("여유를 둔 회차 기간과 겹치지 않으면 세지 않는다")
        void outOfRange() {
            LineupSelection selection = 고른다(List.of(축제("1", "겨울 벚꽃 조명", "01-10", "02-20", 영등포),
                축제("2", "이른 벚꽃 축제", "02-25", "03-06", 하동)));

            assertThat(selection.themedFestivals()).isEqualTo(1);
            assertThat(근거_지역(selection)).containsExactly(지역("KR-38360"));
        }

        @Test
        @DisplayName("같은 축제(같은 콘텐츠 id)가 두 번 와도 한 번만 센다")
        void duplicate() {
            LineupSelection selection = 고른다(List.of(축제("1", "석촌호수 벚꽃 축제", "04-01", "04-07", 송파),
                축제("1", "석촌호수 벚꽃 축제", "04-01", "04-07", 송파), 축제("2", "보문 벚꽃 축제", "03-30", "04-06", 경주),
                축제("3", "경주 벚꽃 마라톤", "04-05", "04-05", 경주)));

            assertThat(근거_지역(selection)).containsExactly(지역("KR-37020"), 지역("KR-11240"));
        }

        @Test
        @DisplayName("우리 지역을 찾지 못한 축제는 빼고 경고한다")
        void unlocated() {
            LineupSelection selection = 고른다(List.of(축제("1", "바다 위 벚꽃 축제", "04-01", "04-02", 먼바다)));

            assertThat(selection.unlocatedFestivals()).isEqualTo(1);
            assertThat(selection.warnings()).anyMatch(warning -> warning.contains("지역을 찾지 못한 축제·관광지 1건"));
        }
    }

    @Nested
    @DisplayName("관광지 근거는")
    class Attractions {

        @Test
        @DisplayName("축제가 없는 지역도 계절 관광지가 있으면 근거 지역이 되고 근거 종류가 관광지로 남는다(기간 없음)")
        void attractionOnly() {
            LineupSelection selection = 고른다(List.of(), List.of(관광지("a1", "단풍산", 영월)));

            LineupRegion 영월군 = selection.regions().stream().findFirst().orElseThrow();
            assertThat(영월군.code()).isEqualTo(지역("KR-32330"));
            assertThat(영월군.provenance()).isEqualTo(LineupProvenance.TOURAPI);
            assertThat(영월군.evidence().getFirst().kind()).isEqualTo(LineupEvidence.EvidenceKind.ATTRACTION);
            assertThat(영월군.evidence().getFirst().startDate()).isNull();
        }

        @Test
        @DisplayName("축제 근거가 있는 지역이 관광지만 있는 지역보다 앞선다")
        void festivalsFirst() {
            LineupSelection selection = 고른다(List.of(축제("1", "진해 벚꽃 축제", "03-27", "04-05", 진해)),
                List.of(관광지("a1", "청풍 벚꽃길", LineupFixtures.제천), 관광지("a2", "벚꽃 언덕", LineupFixtures.제천)));

            assertThat(근거_지역(selection)).containsExactly(지역("KR-38115"), 지역("KR-33030"));
        }

        @Test
        @DisplayName("관광지만 있는 지역끼리는 관광지 수가 많은 순, 같으면 지역 코드 순이다")
        void attractionRanking() {
            LineupSelection selection = 고른다(List.of(), List.of(관광지("a1", "단풍산", 영월), 관광지("a2", "철암 단풍군락지", LineupFixtures.태백),
                관광지("a3", "단풍 계곡", 영월)));

            assertThat(근거_지역(selection)).containsExactly(지역("KR-32330"), 지역("KR-32050"));
        }

        @Test
        @DisplayName("축제와 관광지가 함께 있는 지역은 축제 근거를 먼저, 관광지 근거를 뒤에 남긴다")
        void evidenceOrder() {
            LineupSelection selection = 고른다(List.of(축제("1", "진해 벚꽃 축제", "03-27", "04-05", 진해)),
                List.of(관광지("a1", "진해 벚꽃길", 진해)));

            assertThat(selection.regions().stream().findFirst().orElseThrow().evidence()).extracting(LineupEvidence::kind)
                .containsExactly(LineupEvidence.EvidenceKind.FESTIVAL, LineupEvidence.EvidenceKind.ATTRACTION);
        }

        @Test
        @DisplayName("같은 관광지(콘텐츠 id)가 여러 검색어에서 나와도 한 번만 센다")
        void duplicate() {
            LineupSelection selection = 고른다(List.of(), List.of(관광지("a1", "단풍산", 영월), 관광지("a1", "단풍산", 영월),
                관광지("a2", "철암 단풍군락지", LineupFixtures.태백), 관광지("a3", "태백 단풍길", LineupFixtures.태백)));

            assertThat(근거_지역(selection)).containsExactly(지역("KR-32050"), 지역("KR-32330"));
        }
    }

    @Nested
    @DisplayName("근거 지역이 모자라면")
    class Shortage {

        @Test
        @DisplayName("기본 목록(AI 추정)에서 아직 없는 지역을 정의 순서대로 채우고 출처를 가른다")
        void fillsFromDefaults() {
            LineupSelection selection = 고른다(List.of(축제("1", "진해 벚꽃 축제", "03-27", "04-05", 진해),
                축제("2", "춘천 벚꽃 축제", "04-12", "04-14", LineupFixtures.춘천)));

            assertThat(selection.regions().size()).isEqualTo(10);
            assertThat(근거_지역(selection)).containsExactly(지역("KR-38115"), 지역("KR-32010"));
            List<RegionCode> 채운_지역 = selection.regions().stream().filter(region -> region.provenance() == LineupProvenance.AI_ESTIMATE)
                .map(LineupRegion::code).toList();
            assertThat(채운_지역).containsExactlyElementsOf(봄.regions().stream().filter(code -> !code.equals(지역("KR-38115"))).limit(8).toList());
            assertThat(selection.regions().provenance()).isEqualTo("mixed");
            assertThat(selection.warnings()).anyMatch(warning -> warning.contains("2곳이라 나머지 8곳은 AI 추정"));
        }

        @Test
        @DisplayName("테마 축제가 하나도 없으면 기본 목록 그대로이고 그렇다고 경고한다")
        void nothing() {
            LineupSelection selection = 고른다(List.of());

            assertThat(selection.regions().codes()).containsExactlyElementsOf(봄.regions());
            assertThat(selection.regions().provenance()).isEqualTo("ai-estimate");
            assertThat(selection.warnings()).anyMatch(warning -> warning.contains("맞는 축제·관광지가 없어"));
        }

        @Test
        @DisplayName("쪽 수 상한 때문에 다 읽지 못했으면 순위가 달라질 수 있다고 경고한다")
        void truncated() {
            LineupSelection selection = 봄_고르기().select(봄2027, new FestivalFetch.Fetched(벚꽃_열두_지역(), 조회시각, true));

            assertThat(selection.warnings()).anyMatch(warning -> warning.contains("앞쪽 일부만"));
        }
    }
}
