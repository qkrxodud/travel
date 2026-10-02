package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.progression.infra.entity.QuestProgressJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuestProgressJpaRepository extends JpaRepository<QuestProgressJpaEntity, QuestProgressJpaEntity.Key> {

    List<QuestProgressJpaEntity> findByExplorerIdAndQuestPeriod(String explorerId, String questPeriod);

    List<QuestProgressJpaEntity> findByExplorerId(String explorerId);
}
