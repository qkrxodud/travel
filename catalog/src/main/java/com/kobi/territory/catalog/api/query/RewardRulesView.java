package com.kobi.territory.catalog.api.query;

import com.kobi.territory.common.model.Rarity;
import java.util.Map;

/** 보상 규칙 값(공개). 8단계: mysteryBonus(이번 주 미스터리 지역)·provinceConquestBonus(시·도 정복) 추가 — 끝에, 하위 호환. */
public record RewardRulesView(Map<Rarity, Integer> xpByRarity, int provinceFirstBonus, int setCompleteBonus, int claimBonus,
                              int mysteryBonus, int provinceConquestBonus) {}
