package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Optional;

/**
 * 진행 저장소. 낙관적 락(version). save 는 평소엔 새로 쌓인 장부·뱃지·칭호와 바뀐 지역만 쓰고(짧은 트랜잭션),
 * 재계산 사본({@link ExplorerProgress#rebuilt()})이면 통째로 동기화한다.
 */
public interface ExplorerProgressRepository {

    Optional<ExplorerProgress> find(ExplorerId explorerId);

    void save(ExplorerProgress progress);

    /** 탐험가 단위 지역 기록만(퀘스트의 "처음 가는 시·도" 판정용 읽기). 없으면 빈 기록. */
    ExploredRegions exploredRegions(ExplorerId explorerId);
}
