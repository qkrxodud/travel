package com.kobi.territory.progression.domain.collectionbook;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;

/** 테마가 이번 체크인으로 처음 완성됨 → application 이 SetCompleted(공개 이벤트, 이름 유지)로 적재한다. */
public record ThemeCompletion(String mapId, String themeId, ExplorerId completedBy, Instant completedAt) {}
