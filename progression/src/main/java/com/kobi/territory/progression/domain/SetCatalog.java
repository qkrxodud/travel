package com.kobi.territory.progression.domain;

import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import java.util.Optional;

/** 일급 컬렉션: 도감 세트 정의 목록(정책 VO — application 이 카탈로그에서 조립). */
public record SetCatalog(List<CollectionSet> sets) {

    public SetCatalog {
        sets = List.copyOf(sets);
    }

    /** 이 지역이 속한 세트들. */
    public List<CollectionSet> containing(RegionCode code) {
        return sets.stream().filter(set -> set.regions().contains(code)).toList();
    }

    public boolean inAnySet(RegionCode code) {
        return sets.stream().anyMatch(set -> set.regions().contains(code));
    }

    public Optional<CollectionSet> find(String setId) {
        return sets.stream().filter(set -> set.id().equals(setId)).findFirst();
    }
}
