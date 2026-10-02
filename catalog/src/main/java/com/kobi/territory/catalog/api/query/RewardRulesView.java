package com.kobi.territory.catalog.api.query;

import com.kobi.territory.common.model.Rarity;
import java.util.Map;

public record RewardRulesView(Map<Rarity, Integer> xpByRarity, int provinceFirstBonus, int setCompleteBonus, int claimBonus) {}
