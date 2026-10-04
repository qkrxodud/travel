package com.kobi.territory.analytics.domain.actor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 방문이 처음 들어온 길(첫 화면 이벤트 {@code app_open.entry}). 공개 카드·프로필 링크로 들어와 가입하면 "카드 유입"(K 계수의 한 갈래).
 * 초대코드 링크로 들어온 것은 길만 적고, 초대 유입은 실제 초대 합류(서버 사실)로 센다.
 */
public enum EntryPoint {
    DIRECT, INVITE, PROFILE, CARD, OTHER;

    public static Optional<EntryPoint> fromLabel(String label) {
        if (label == null) return Optional.empty();
        return Arrays.stream(values()).filter(entry -> entry.label().equals(label.toLowerCase(Locale.ROOT))).findFirst();
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 카드 유입으로 치는 들어온 길의 이름들(집계 질의 인자). */
    public static List<String> sharedCardLabels() {
        return Arrays.stream(values()).filter(EntryPoint::fromSharedCard).map(EntryPoint::label).toList();
    }

    /** 공개 카드·프로필 링크로 들어온 길 — 이 방문이 가입하면 카드 유입 가입이다. */
    public boolean fromSharedCard() {
        return this == CARD || this == PROFILE;
    }
}
