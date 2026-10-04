package com.kobi.territory.exploration.infra.repository;

import com.kobi.territory.exploration.infra.entity.WishPinJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface WishPinJpaRepository extends JpaRepository<WishPinJpaEntity, WishPinJpaEntity.Key> {

    List<WishPinJpaEntity> findByExplorerId(String explorerId);
}
