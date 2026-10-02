package com.kobi.territory.progression.infra.repository;

import com.kobi.territory.progression.infra.entity.TitleEarnedJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface TitleEarnedJpaRepository extends JpaRepository<TitleEarnedJpaEntity, TitleEarnedJpaEntity.Key> {

    List<TitleEarnedJpaEntity> findByExplorerId(String explorerId);
}
