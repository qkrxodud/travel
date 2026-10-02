package com.kobi.territory.progression.infra.entity;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** 집합 성격 컬럼(VARCHAR 쉼표 구분 — V2 참고)의 읽기·쓰기. 정렬해서 써 같은 집합이면 같은 문자열이 된다. */
final class CsvColumn {

    private CsvColumn() {}

    static Set<String> read(String csv) {
        return csv == null || csv.isBlank() ? Set.of()
            : Arrays.stream(csv.split(",")).filter(token -> !token.isBlank()).collect(Collectors.toSet());
    }

    static String write(Collection<String> values) {
        return values.stream().sorted().collect(Collectors.joining(","));
    }
}
