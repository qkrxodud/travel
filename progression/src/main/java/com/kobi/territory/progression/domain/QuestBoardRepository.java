package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.ExplorerId;
import java.util.List;

/** 퀘스트 보드 저장소. 행이 없으면 빈 보드. */
public interface QuestBoardRepository {

    QuestBoard load(ExplorerId explorerId, QuestPeriod period);

    /** 탐험가의 보드 전부(지난 달 포함 — 재계산 복구 규칙용). */
    List<QuestBoard> loadAll(ExplorerId explorerId);

    void save(QuestBoard board);
}
