package com.kobi.territory.progression.domain.collectionbook;

import com.kobi.territory.common.model.RegionCode;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/**
 * 계절 한정 테마(설계 §7 "계절 한정 세트", 9단계) 정의 — 정책 VO, application 이 카탈로그 seasons.json 에서 조립한다.
 * 해마다 start ~ end(양 끝 포함, 서비스 시간대 날짜)에 새 회차가 열린다. 읽기 전용 참조 데이터라 record.
 */
public record Season(String id, MonthDay start, MonthDay end, Set<RegionCode> regions) {

    public Season {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        regions = Set.copyOf(regions);
        if (start.isAfter(end)) throw new IllegalArgumentException("계절 기간은 한 해 안에서: " + id);
        if (regions.isEmpty()) throw new IllegalArgumentException("계절 지역 없음: " + id);
    }

    /** 그 해의 회차 — [start 0시, end 다음 날 0시). */
    public SeasonRound roundOf(int year, ZoneId zone) {
        return new SeasonRound(id + "-" + year, id, year, start.atYear(year).atStartOfDay(zone).toInstant(),
            end.atYear(year).plusDays(1).atStartOfDay(zone).toInstant(), regions);
    }
}
