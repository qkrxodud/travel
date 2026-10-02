package com.kobi.territory.progression.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface TitleEarnedJpaRepository extends JpaRepository<TitleEarnedJpaEntity, TitleEarnedJpaEntity.Key> {

    List<TitleEarnedJpaEntity> findByExplorerId(String explorerId);
}
