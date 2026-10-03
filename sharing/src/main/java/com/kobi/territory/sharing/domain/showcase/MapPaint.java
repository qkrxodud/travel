package com.kobi.territory.sharing.domain.showcase;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 일급 컬렉션: 카드 지도의 칠(지역 코드 → 색 역할) + 강조 지역 + 고리 표시(전설). 칠하지 않은 지역은 바탕색. */
public final class MapPaint {

    private final Map<String, Tone> toneByCode;
    private final String highlighted;
    private final Set<String> ringed;

    private MapPaint(Map<String, Tone> toneByCode, String highlighted, Set<String> ringed) {
        this.toneByCode = Map.copyOf(toneByCode);
        this.highlighted = highlighted;
        this.ringed = Set.copyOf(ringed);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Tone> toneOf(String regionCode) {
        return Optional.ofNullable(toneByCode.get(regionCode));
    }

    /** 테두리·원으로 강조할 지역(없으면 빈 값). */
    public Optional<String> highlighted() {
        return Optional.ofNullable(highlighted);
    }

    public boolean ringed(String regionCode) {
        return ringed.contains(regionCode);
    }

    public static final class Builder {
        private final Map<String, Tone> toneByCode = new LinkedHashMap<>();
        private String highlighted;
        private Set<String> ringed = Set.of();

        private Builder() {}

        public Builder paint(Set<String> regionCodes, Tone tone) {
            regionCodes.forEach(code -> toneByCode.put(code, tone));
            return this;
        }

        public Builder highlight(String regionCode) {
            this.highlighted = regionCode;
            if (regionCode != null) toneByCode.put(regionCode, Tone.HIGHLIGHT);
            return this;
        }

        public Builder ring(Set<String> regionCodes) {
            this.ringed = Set.copyOf(regionCodes);
            return this;
        }

        public MapPaint build() {
            return new MapPaint(toneByCode, highlighted, ringed);
        }
    }
}
