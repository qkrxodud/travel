package com.kobi.territory.progression.infra;

import com.kobi.territory.progression.infra.ProgressJpaEntities.BadgeRow;
import com.kobi.territory.progression.infra.ProgressJpaEntities.LedgerRow;
import com.kobi.territory.progression.infra.ProgressJpaEntities.ProgressRow;
import com.kobi.territory.progression.infra.ProgressJpaEntities.QuestRow;
import com.kobi.territory.progression.infra.ProgressJpaEntities.RegionRow;
import com.kobi.territory.progression.infra.ProgressJpaEntities.SetRow;
import com.kobi.territory.progression.infra.ProgressJpaEntities.TitleRow;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProgressRowRepository extends JpaRepository<ProgressRow, String> {}

interface LedgerRowRepository extends JpaRepository<LedgerRow, Long> {
    List<LedgerRow> findByExplorerIdOrderByIdAsc(String explorerId);
}

interface BadgeRowRepository extends JpaRepository<BadgeRow, BadgeRow.Key> {
    List<BadgeRow> findByExplorerId(String explorerId);
}

interface TitleRowRepository extends JpaRepository<TitleRow, TitleRow.Key> {
    List<TitleRow> findByExplorerId(String explorerId);
}

interface RegionRowRepository extends JpaRepository<RegionRow, RegionRow.Key> {
    List<RegionRow> findByExplorerId(String explorerId);
}

interface SetRowRepository extends JpaRepository<SetRow, SetRow.Key> {
    List<SetRow> findByMapId(String mapId);
}

interface QuestRowRepository extends JpaRepository<QuestRow, QuestRow.Key> {
    List<QuestRow> findByExplorerIdAndQuestPeriod(String explorerId, String questPeriod);

    List<QuestRow> findByExplorerId(String explorerId);
}
