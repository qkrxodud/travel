package com.kobi.territory.progression.api.query;

import java.time.Instant;

/** 지도에서 완성된 테마(세트) 하나와 완성 시각(공개 Query DTO — 꾸미기가 세트 보상의 기간을 완성 시각으로 판정할 때 쓴다). */
public record CompletedSetView(String setId, Instant completedAt) {}
