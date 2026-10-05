package com.kobi.territory.catalog.domain.region;

import java.util.Objects;

/** 시·도(17개). 정복률 집계 단위. */
public record Province(String code, String name, String fullName, int displayOrder, int regionCount) {
    public Province {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }

    /**
     * 주소 첫 낱말이 이 시·도를 가리키는지: 정식 이름("강원특별자치도"), 짧은 이름으로 시작("강원도"·"서울특별시"), 옛 정식 이름의 첫·셋째 글자
     * ("전라북도" → 전북, "경상남도" → 경남 — 이름이 바뀌기 전 주소).
     */
    public boolean isNamedBy(String addressWord) {
        if (addressWord == null || addressWord.isBlank()) return false;
        if (addressWord.equals(fullName) || addressWord.startsWith(name)) return true;
        return addressWord.length() >= 4 && name.length() == 2
            && name.equals(String.valueOf(addressWord.charAt(0)) + addressWord.charAt(2));
    }
}
