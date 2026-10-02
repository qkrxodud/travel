package com.kobi.territory.progression.domain;

import java.util.Objects;

/**
 * 칭호 획득 조건. LEVEL(ref=레벨), SET(ref=세트 id, 탐험가 기준 완성 보상을 받음), QUEST(ref=퀘스트 id, 보상 받기 완료),
 * PROVINCE(ref=시·도 코드, 활성 지역으로 100%).
 */
public record TitleRule(String id, Source source, String ref) {

    public enum Source { LEVEL, SET, QUEST, PROVINCE }

    public TitleRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(ref, "ref");
    }
}
