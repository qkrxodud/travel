package com.kobi.territory.catalog.api.query;

/** 보상 한 줄. source: REGION_BASE | PROVINCE_FIRST | FIRST_CLAIM | SET_COMPLETE | QUEST */
public record RewardLineView(String source, int amount) {}
