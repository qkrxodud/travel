package com.kobi.territory.catalog.domain.lineup;

import com.kobi.territory.catalog.domain.catalog.Catalog;
import com.kobi.territory.catalog.domain.definition.SeasonDefinition;
import com.kobi.territory.catalog.domain.definition.SeasonRoundWindow;
import com.kobi.territory.catalog.domain.region.GeoPoint;
import com.kobi.territory.catalog.domain.region.RegionLocator;
import com.kobi.territory.catalog.infra.repository.JsonCatalogRepository;
import com.kobi.territory.common.model.RegionCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.List;

/** 회차 지역 목록 테스트 준비 — 실제 카탈로그(경계·지역)와 봄(벚꽃) 정의, 지역 안쪽 좌표. 축제는 테스트용 가짜다. */
final class LineupFixtures {

    static final ZoneId 서울 = ZoneId.of("Asia/Seoul");
    static final Catalog 카탈로그 = new JsonCatalogRepository().load();
    static final RegionLocator 위치찾기 = 카탈로그.regionLocator(3);
    static final SeasonDefinition 봄 = 카탈로그.progression().seasons().stream().filter(season -> season.id().equals("spring"))
        .findFirst().orElseThrow();
    static final SeasonRoundWindow 봄2027 = 봄.windowOf(2027, 서울);
    static final LineupPolicy 열곳_여유14일 = new LineupPolicy(10, 14, 5);
    static final Instant 조회시각 = Instant.parse("2027-02-10T00:00:00Z");

    static final GeoPoint 진해 = new GeoPoint(128.76433, 35.137);
    static final GeoPoint 경주 = new GeoPoint(129.22767, 35.84533);
    static final GeoPoint 영등포 = new GeoPoint(126.9092, 37.5244);
    static final GeoPoint 송파 = new GeoPoint(127.12027, 37.505);
    static final GeoPoint 하동 = new GeoPoint(127.7942, 35.1406);
    static final GeoPoint 강릉 = new GeoPoint(128.8605, 37.7105);
    static final GeoPoint 군산 = new GeoPoint(126.80487, 35.9675);
    static final GeoPoint 제주 = new GeoPoint(126.51193, 33.43903);
    static final GeoPoint 구례 = new GeoPoint(127.4965, 35.24743);
    static final GeoPoint 춘천 = new GeoPoint(127.73307, 37.90663);
    static final GeoPoint 울산남구 = new GeoPoint(129.33453, 35.5186);
    static final GeoPoint 먼바다 = new GeoPoint(125.0, 34.0);
    static final GeoPoint 제천 = new GeoPoint(128.168, 37.1235);
    static final GeoPoint 영월 = new GeoPoint(128.45497, 37.217);
    static final GeoPoint 태백 = new GeoPoint(128.9825, 37.13773);

    private LineupFixtures() {}

    static RegionCode 지역(String code) {
        return RegionCode.of(code);
    }

    /** 2027년 그 월·일에 여는 축제. */
    static Festival 축제(String contentId, String title, String from, String to, GeoPoint where) {
        return new Festival(contentId, title, MonthDay.parse("--" + from).atYear(2027), MonthDay.parse("--" + to).atYear(2027), where, null);
    }

    static Festival 주소만_있는_축제(String contentId, String title, String from, String to, String address) {
        return new Festival(contentId, title, MonthDay.parse("--" + from).atYear(2027), MonthDay.parse("--" + to).atYear(2027), null, address);
    }

    static Attraction 관광지(String contentId, String title, GeoPoint where) {
        return new Attraction(contentId, title, where, null);
    }

    static FestivalFetch.Fetched 읽음(List<Festival> festivals, List<Attraction> attractions) {
        return new FestivalFetch.Fetched(festivals, attractions, 조회시각, false);
    }

    static FestivalFetch.Fetched 읽음(List<Festival> festivals) {
        return new FestivalFetch.Fetched(festivals, 조회시각, false);
    }

    static LineupSelector 봄_고르기() {
        return new LineupSelector(봄, 위치찾기, 열곳_여유14일);
    }

    /** 벚꽃 축제 열두 지역: 진해·경주 두 건, 영등포 10일, 송파·강릉 7일(동률), 하동 4일, 구례·제주·춘천·충주(주소만) 3일, 울산 남구·군산 2일. */
    static List<Festival> 벚꽃_열두_지역() {
        return List.of(
            축제("1", "진해 벚꽃 축제", "03-27", "04-05", 진해), 축제("2", "진해 벚꽃 야행", "04-01", "04-03", 진해),
            축제("3", "경주 벚꽃 마라톤", "04-05", "04-05", 경주), 축제("4", "보문 벚꽃 축제", "03-30", "04-06", 경주),
            축제("5", "여의도 벚꽃 축제", "04-04", "04-13", 영등포), 축제("6", "석촌호수 벚꽃 축제", "04-01", "04-07", 송파),
            축제("7", "하동 벚꽃길 축제", "03-29", "04-01", 하동), 축제("8", "경포 벚꽃 축제", "04-03", "04-09", 강릉),
            축제("9", "은파 벚꽃 축제", "04-05", "04-06", 군산), 축제("10", "제주 왕벚꽃 축제", "03-24", "03-26", 제주),
            축제("11", "구례 벚꽃 축제", "03-28", "03-30", 구례), 축제("12", "춘천 벚꽃 축제", "04-12", "04-14", 춘천),
            주소만_있는_축제("13", "충주 벚꽃 축제", "04-10", "04-12", "충청북도 충주시 중앙로 1"),
            축제("14", "울산 벚꽃 축제", "04-02", "04-03", 울산남구));
    }

    static CollectionSchedule 서른날전부터_7일마다_자동확정() {
        return new CollectionSchedule(30, Duration.ofDays(7), true, 10, 서울);
    }

    static LocalDate 날(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    /** 서울 그 날 정오. */
    static Instant 그날_정오(int year, int month, int day) {
        return LocalDate.of(year, month, day).atTime(12, 0).atZone(서울).toInstant();
    }
}
