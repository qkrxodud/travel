package com.kobi.territory.catalog.domain.definition;

import java.util.Objects;

/**
 * 칭호 정의. 출처별로 얻는 조건이 다르다 — LEVEL(ref=레벨), SET(ref=세트 id), QUEST(ref=퀘스트 id),
 * PROVINCE(ref=시·도 코드, 100% 정복), STREAK(ref=마일스톤 개월 수, 8단계), SEASON(ref=계절 id, 9단계 — 회차 무관 하나). id 형식은 프로토타입과 같다: lv{n}, set-{id}, long-{id},
 * own-{시·도 코드}, 그리고 streak-{개월}·season-{계절}.
 *
 * @param how 얻는 방법(화면 표시용)
 */
public record TitleDefinition(String id, String name, String how, Source source, String ref) {

    public enum Source { LEVEL, SET, QUEST, PROVINCE, STREAK, SEASON }

    public TitleDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(ref, "ref");
    }
}
