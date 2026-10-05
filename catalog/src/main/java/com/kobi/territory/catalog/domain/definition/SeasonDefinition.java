package com.kobi.territory.catalog.domain.definition;

import com.kobi.territory.common.model.RegionCode;
import java.time.Instant;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 계절 한정 테마 정의(seasons.json, 9단계). 해마다 {@code start ~ end}(양 끝 포함, 서비스 시간대 날짜) 동안 새 회차
 * {@code {id}-{연도}}(예: autumn-2026)로 열린다. 회차 안에 처리된 체크인만 세고, 기간이 끝나면 미완성 진행은 닫힌다 — 판정은 진행 도메인이
 * 한다(카탈로그는 값만 보관한다). 완성 보상: XP(reward-rules.json seasonCompleteBonus)·칭호(season-{id}, 회차 무관 하나)·
 * 회차별 계절 배경(아이템 정의 DB, SEASON_COMPLETE 규칙 — grantRef = 회차 id).
 *
 * @param start 매년 열리는 날(MonthDay — 연도 경계를 넘지 않는다: start ≤ end)
 * @param end   매년 닫히는 날(이 날까지 포함)
 * @param title      칭호 이름
 * @param regions    기본 지역 목록 — 회차별 확정 목록(13s단계 season_lineup)이 없을 때 쓴다
 * @param keywords   계절 테마 키워드(13s단계) — 축제 이름에 하나라도 들어 있으면 이 계절의 축제(공백 무시, 대소문자 무시)
 * @param provenance 기본 지역 목록의 출처 표시(실제 세계 데이터의 출처 규칙) — 지금은 {@value #AI_ESTIMATE}(AI 일반 지식 추정, 검증 전)
 * @param attractionKeywords 계절 관광지 키워드 검색어(13s단계 보강 — TourAPI 키워드 검색, 키워드 하나 = 호출 한 번). 검색은 이름 부분 일치라
 *                           "단풍길"처럼 이미 있는 검색어를 담은 말은 더해도 새로 나오지 않는다
 */
public record SeasonDefinition(String id, String name, String desc, MonthDay start, MonthDay end, List<RegionCode> regions,
                               String title, String emoji, List<String> keywords, String provenance, List<String> attractionKeywords) {

    /** AI 가 일반 지식으로 추정한 값(검증 전). */
    public static final String AI_ESTIMATE = "ai-estimate";

    private static final Pattern ID = Pattern.compile("^[a-z]{1,12}$");
    private static final Pattern ROUND_ID = Pattern.compile("^([a-z]{1,12})-(\\d{4})$");

    public SeasonDefinition {
        Objects.requireNonNull(id, "id");
        if (!ID.matcher(id).matches()) throw new IllegalStateException("계절 id 는 영문 소문자: " + id);
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(title, "title");
        if (start.isAfter(end)) throw new IllegalStateException("계절 기간은 한 해 안에서(시작 ≤ 끝): " + id);
        regions = List.copyOf(regions);
        if (regions.isEmpty() || regions.stream().distinct().count() != regions.size()) {
            throw new IllegalStateException("계절 지역이 비었거나 중복: " + id);
        }
        keywords = keywords == null ? List.of() : keywords.stream().map(String::strip).filter(word -> !word.isEmpty()).distinct().toList();
        provenance = provenance == null || provenance.isBlank() ? AI_ESTIMATE : provenance;
        attractionKeywords = attractionKeywords == null ? List.of()
            : attractionKeywords.stream().map(String::strip).filter(word -> !word.isEmpty()).distinct().toList();
    }

    /** 관광지 키워드 없이 — 13s단계 보강 이전. */
    public SeasonDefinition(String id, String name, String desc, MonthDay start, MonthDay end, List<RegionCode> regions, String title,
                            String emoji, List<String> keywords, String provenance) {
        this(id, name, desc, start, end, regions, title, emoji, keywords, provenance, List.of());
    }

    /** 키워드·출처 없이 — 13s단계 이전 정의(출처는 AI 추정으로 본다). */
    public SeasonDefinition(String id, String name, String desc, MonthDay start, MonthDay end, List<RegionCode> regions, String title,
                            String emoji) {
        this(id, name, desc, start, end, regions, title, emoji, List.of(), AI_ESTIMATE, List.of());
    }

    /** 축제 이름이 이 계절 테마에 맞는지(키워드 하나라도 포함 — 공백·대소문자 무시). 키워드가 없으면 아무것도 맞지 않는다. */
    public boolean themeMatches(String festivalTitle) {
        if (festivalTitle == null) return false;
        String title = normalized(festivalTitle);
        return keywords.stream().map(SeasonDefinition::normalized).anyMatch(title::contains);
    }

    /** 그 해 회차의 기간(서비스 시간대 날짜). */
    public SeasonRoundWindow windowOf(int year, ZoneId zone) {
        var firstDay = start.atYear(year);
        var lastDay = end.atYear(year);
        return new SeasonRoundWindow(roundId(year), id, year, firstDay, lastDay, firstDay.atStartOfDay(zone).toInstant(),
            lastDay.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /** 이 시각 뒤에 처음 열리는 회차(아직 시작하지 않은 것 — 올해 것이 지났거나 열려 있으면 다음 해). */
    public SeasonRoundWindow upcomingWindow(Instant at, ZoneId zone) {
        int year = at.atZone(zone).getYear();
        SeasonRoundWindow thisYear = windowOf(year, zone);
        return thisYear.startedBy(at) ? windowOf(year + 1, zone) : thisYear;
    }

    /** 이 시각에 열려 있는 회차. */
    public Optional<SeasonRoundWindow> openWindow(Instant at, ZoneId zone) {
        return Optional.of(windowOf(at.atZone(zone).getYear(), zone)).filter(window -> window.openAt(at));
    }

    /** 회차 id 의 연도. 형식이 아니면 빈 값. */
    public static Optional<Integer> yearOf(String roundId) {
        if (roundId == null) return Optional.empty();
        var matcher = ROUND_ID.matcher(roundId);
        return matcher.matches() ? Optional.of(Integer.parseInt(matcher.group(2))) : Optional.empty();
    }

    private static String normalized(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** 칭호 id(season-{id}) — 회차와 무관하게 하나. */
    public String titleId() {
        return "season-" + id;
    }

    /** 이 계절의 그 해 회차 id. */
    public String roundId(int year) {
        return id + "-" + year;
    }

    /** 회차 id(season-연도)에서 계절 id. 형식이 아니면 null. */
    public static String seasonIdOf(String roundId) {
        if (roundId == null) return null;
        var matcher = ROUND_ID.matcher(roundId);
        return matcher.matches() ? matcher.group(1) : null;
    }
}
