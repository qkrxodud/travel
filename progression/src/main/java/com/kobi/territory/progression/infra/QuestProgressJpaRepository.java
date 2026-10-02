package com.kobi.territory.progression.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuestProgressJpaRepository extends JpaRepository<QuestProgressJpaEntity, QuestProgressJpaEntity.Key> {

    List<QuestProgressJpaEntity> findByExplorerIdAndQuestPeriod(String explorerId, String questPeriod);

    List<QuestProgressJpaEntity> findByExplorerId(String explorerId);
}
