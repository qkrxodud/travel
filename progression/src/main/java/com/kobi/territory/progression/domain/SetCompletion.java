package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;

/** 세트가 이번 체크인으로 처음 완성됨 → application 이 SetCompleted(공개 이벤트)로 적재한다. */
public record SetCompletion(String mapId, String setId, ExplorerId completedBy, Instant completedAt) {}
