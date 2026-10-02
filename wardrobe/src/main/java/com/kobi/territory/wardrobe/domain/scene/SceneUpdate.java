package com.kobi.territory.wardrobe.domain.scene;

import java.time.Instant;

/** 장면이 실제로 바뀐 결과(바뀌지 않았으면 없음). application 이 SceneChanged 를 outbox 에 적재한다. */
public record SceneUpdate(Instant at) {}
