package com.kobi.territory.progression.domain.collectionbook;

import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import java.util.Optional;

/** 일급 컬렉션: 테마(도감 세트) 정의 목록 — 정책 VO, application 이 카탈로그에서 조립한다. */
public final class Themes {

    private final List<Theme> items;

    public Themes(List<Theme> themes) {
        this.items = List.copyOf(themes);
    }

    /** 이 지역이 속한 테마들. */
    public List<Theme> containing(RegionCode region) {
        return items.stream().filter(theme -> theme.includes(region)).toList();
    }

    /** 이 지역이 어느 테마에든 속하는지. */
    public boolean includeAny(RegionCode region) {
        return items.stream().anyMatch(theme -> theme.includes(region));
    }

    public Optional<Theme> find(String themeId) {
        return items.stream().filter(theme -> theme.id().equals(themeId)).findFirst();
    }
}
