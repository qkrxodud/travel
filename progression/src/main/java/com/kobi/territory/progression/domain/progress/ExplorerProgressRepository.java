package com.kobi.territory.progression.domain.progress;

import com.kobi.territory.common.model.ExplorerId;
import java.util.Optional;

/**
 * 진행 저장소(애그리거트 단위). 낙관적 락(version — 자식 행만 바뀌어도 증가).
 * 무엇을 할지는 호출자가 고른다: 평소 커맨드는 {@link #save}(새로 쌓인 장부·뱃지·칭호와 바뀐 지역만 반영),
 * 재계산처럼 통째로 바꾸는 경로는 {@link #replace}(그 탐험가의 자식 행을 지우고 다시 넣음).
 */
public interface ExplorerProgressRepository {

    Optional<ExplorerProgress> find(ExplorerId explorerId);

    /**
     * 루트 행을 배타 잠금(PESSIMISTIC_WRITE)한 뒤 불러온다 — 재계산처럼 읽고 통째로 바꾸는 경로용. 잠금이 풀릴 때까지
     * 동시 이벤트 처리의 갱신은 기다렸다가 version 충돌로 재시도되므로, 재계산과 릴레이가 겹쳐도 반영분이 사라지지 않는다.
     */
    Optional<ExplorerProgress> findLocked(ExplorerId explorerId);

    void save(ExplorerProgress progress);

    /** 통째로 바꾸기. progress 는 {@link #findLocked}로 불러온(루트를 잠근) 것이어야 한다. */
    void replace(ExplorerProgress progress);

    /** 탐험가 단위 지역 기록만(퀘스트의 "처음 가는 시·도" 판정용 읽기). 없으면 빈 기록. */
    ExploredRegions exploredRegions(ExplorerId explorerId);
}
