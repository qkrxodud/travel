package com.kobi.territory.sharing.domain.card;

import com.kobi.territory.sharing.domain.SharingError;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 자랑 카드 종류(§2-8): 영토 · 최근 여행 · 연간 리캡 · VS(두 탐험가 비교). 경로 값은 소문자(territory, recent, recap). */
public enum CardKind {
    TERRITORY, RECENT, RECAP, VS;

    /** 한 탐험가 카드(경로 /u/{handle}/card/{kind}.png 로 열리는 것). VS 는 /u/{handle}/vs/{other}.png. */
    public static final List<CardKind> SOLO = List.of(TERRITORY, RECENT, RECAP);

    /** 경로 값 → 종류(대소문자 무시). 한 탐험가 카드가 아니면 CARD_KIND_NOT_FOUND. */
    public static CardKind parseSolo(String raw) {
        String normalized = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(kind -> kind != VS && kind.name().equals(normalized)).findFirst()
            .orElseThrow(() -> SharingError.CARD_KIND_NOT_FOUND.exception(String.valueOf(raw)));
    }

    public String pathValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
