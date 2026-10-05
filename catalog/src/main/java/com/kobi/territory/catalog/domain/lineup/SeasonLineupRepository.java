package com.kobi.territory.catalog.domain.lineup;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 포트: 회차 지역 목록 저장소(season_lineup·season_lineup_region). */
public interface SeasonLineupRepository {

    Optional<SeasonLineup> find(String roundId);

    List<SeasonLineup> findAll(Collection<String> roundIds);

    /** 새로 만들거나 고친다. 그 사이 다른 쪽이 고쳤으면(버전 불일치) 낙관적 잠금 실패. */
    void save(SeasonLineup lineup);
}
