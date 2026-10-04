package com.kobi.territory.progression.domain.collectionbook;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.util.Objects;

/**
 * 계절 회차에 센 방문 하나 — (지역, 칠한 멤버). 회차 기간 안에 처리된 체크인만 세므로, 지도에 그 지역이 남아 있어도 기간 밖에 칠한 방문은
 * 표시가 없다. 그래서 진행은 테마처럼 "지도에 칠해진 지역"이 아니라 이 표시들의 지역이다. 저장 표현 "KR-xxxxx|explorerId".
 */
public record SeasonMark(RegionCode region, ExplorerId member) {

    private static final String SEPARATOR = "|";

    public SeasonMark {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(member, "member");
    }

    public String key() {
        return region.value() + SEPARATOR + member.value();
    }

    public static SeasonMark parse(String key) {
        int at = key.indexOf(SEPARATOR);
        return new SeasonMark(RegionCode.of(key.substring(0, at)), ExplorerId.of(key.substring(at + 1)));
    }
}
