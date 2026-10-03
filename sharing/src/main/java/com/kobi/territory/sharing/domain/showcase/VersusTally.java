package com.kobi.territory.sharing.domain.showcase;

/** 두 탐험가 영토 비교(VS 카드): 나만 · 둘 다 · 상대만. */
public record VersusTally(int onlyMine, int both, int onlyTheirs) {
    public VersusTally {
        if (onlyMine < 0 || both < 0 || onlyTheirs < 0) throw new IllegalArgumentException("음수 집계");
    }

    public int mine() {
        return onlyMine + both;
    }

    public int theirs() {
        return onlyTheirs + both;
    }
}
