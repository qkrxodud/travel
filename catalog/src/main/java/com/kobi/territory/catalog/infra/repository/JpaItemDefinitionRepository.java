package com.kobi.territory.catalog.infra.repository;

import com.kobi.territory.catalog.domain.item.ItemDefinition;
import com.kobi.territory.catalog.domain.item.ItemDefinitionRepository;
import com.kobi.territory.catalog.domain.item.ItemDefinitions;
import com.kobi.territory.catalog.infra.entity.ItemDefinitionJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** 아이템 정의 저장소 어댑터(item_definition). 행 ↔ 도메인 변환은 엔티티가 한다. 정렬은 id 순(지역 아이템이 코드 순으로). */
@Repository
class JpaItemDefinitionRepository implements ItemDefinitionRepository {

    private final ItemDefinitionJpaRepository rows;
    private final EntityManager entityManager;

    JpaItemDefinitionRepository(ItemDefinitionJpaRepository rows, EntityManager entityManager) {
        this.rows = rows;
        this.entityManager = entityManager;
    }

    @Override
    public ItemDefinitions loadAll() {
        return ItemDefinitions.of(rows.findAll(Sort.by("itemId")).stream().map(ItemDefinitionJpaEntity::toDomain).toList());
    }

    @Override
    public Optional<ItemDefinition> find(String itemId) {
        return rows.findById(itemId).map(ItemDefinitionJpaEntity::toDomain);
    }

    /** 새 행으로만 넣는다(persist — 같은 id 가 동시에 들어오면 PK 충돌로 실패, 덮어쓰지 않는다). */
    @Override
    public void add(ItemDefinition item) {
        entityManager.persist(ItemDefinitionJpaEntity.from(item));
    }
}
