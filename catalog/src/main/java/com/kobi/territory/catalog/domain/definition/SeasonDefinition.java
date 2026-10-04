package com.kobi.territory.catalog.domain.definition;

import com.kobi.territory.common.model.RegionCode;
import java.time.MonthDay;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 계절 한정 테마 정의(seasons.json, 9단계). 해마다 {@code start ~ end}(양 끝 포함, 서비스 시간대 날짜) 동안 새 회차
 * {@code {id}-{연도}}(예: autumn-2026)로 열린다. 회차 안에 처리된 체크인만 세고, 기간이 끝나면 미완성 진행은 닫힌다 — 판정은 진행 도메인이
 * 한다(카탈로그는 값만 보관한다). 완성 보상: XP(reward-rules.json seasonCompleteBonus)·칭호(season-{id}, 회차 무관 하나)·
 * 회차별 계절 배경(아이템 정의 DB, SEASON_COMPLETE 규칙 — grantRef = 회차 id).
 *
 * @param start 매년 열리는 날(MonthDay — 연도 경계를 넘지 않는다: start ≤ end)
 * @param end   매년 닫히는 날(이 날까지 포함)
 * @param title 칭호 이름
 */
public record SeasonDefinition(String id, String name, String desc, MonthDay start, MonthDay end, List<RegionCode> regions,
                               String title, String emoji) {

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
