package com.kobi.territory.catalog.infra.entity;

import com.kobi.territory.catalog.domain.item.GrantRule;
import com.kobi.territory.catalog.domain.item.ItemDefinition;
import com.kobi.territory.catalog.domain.item.ItemSlot;
import com.kobi.territory.catalog.domain.item.ValidPeriod;
import com.kobi.territory.common.model.Rarity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * item_definition — 아이템 정의 한 행(V3_1 이관 + 운영 추가). 룩은 형태(look)·주색·보조색 세 컬럼,
 * 지급 규칙은 종류(grant_rule)·대상(grant_ref) 두 컬럼이다(조회 조건이 아닌 JSON 대신 타입이 있는 컬럼).
 */
@Entity
@Table(name = "item_definition")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemDefinitionJpaEntity {

    @Id
    @Column(name = "item_id", length = 60)
    private String itemId;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false, length = 16)
    private String emoji;

    @Column(nullable = false, length = 8)
    private String slot;

    @Column(nullable = false, length = 8)
    private String tier;

    @Column(length = 20)
    private String theme;

    @Column(length = 20)
    private String look;

    @Column(name = "color_primary", length = 7)
    private String colorPrimary;

    @Column(name = "color_secondary", length = 7)
    private String colorSecondary;

    @Column(name = "grant_rule", nullable = false, length = 20)
    private String grantRule;

    @Column(name = "grant_ref", length = 40)
    private String grantRef;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ItemDefinitionJpaEntity from(ItemDefinition item) {
        ItemDefinitionJpaEntity entity = new ItemDefinitionJpaEntity();
        entity.itemId = item.itemId();
        entity.name = item.name();
        entity.emoji = item.emoji();
        entity.slot = item.slot().name();
        entity.tier = item.tier().name();
        entity.theme = item.theme();
        if (item.look() != null) {
            entity.look = item.look().type();
            entity.colorPrimary = item.look().primary();
            entity.colorSecondary = item.look().secondary();
        }
        entity.grantRule = item.grantRule().type().name();
        entity.grantRef = item.grantRule().ref();
        entity.validFrom = item.validPeriod().from();
        entity.validTo = item.validPeriod().to();
        entity.createdAt = item.createdAt();
        return entity;
    }

    public ItemDefinition toDomain() {
        return new ItemDefinition(itemId, name, emoji, ItemSlot.valueOf(slot), Rarity.valueOf(tier), theme,
            look == null ? null : new ItemDefinition.Look(look, colorPrimary, colorSecondary),
            GrantRule.of(GrantRule.Type.valueOf(grantRule), grantRef), new ValidPeriod(validFrom, validTo), createdAt);
    }
}
