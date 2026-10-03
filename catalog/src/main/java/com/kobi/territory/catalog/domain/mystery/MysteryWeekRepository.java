package com.kobi.territory.catalog.domain.mystery;

import java.time.LocalDate;
import java.util.Optional;

/** 미스터리 지역 선택 기록(mystery_week) 저장소. 한 주 한 행, 기록은 바뀌지 않는다. */
public interface MysteryWeekRepository {

    Optional<MysteryWeek> find(LocalDate weekStart);

    /** 새 주의 선택을 기록한다. 같은 주가 이미 기록돼 있으면(동시에 고른 다른 요청) 저장 기술의 유일성 위반으로 실패한다. */
    void add(MysteryWeek week);
}
