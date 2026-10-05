package com.kobi.territory.catalog.domain.region;

import com.kobi.territory.common.model.RegionCode;
import java.util.Objects;

/**
 * 바깥 위치를 우리 지역으로 옮긴 결과.
 *
 * @param method 어떻게 찾았는지(좌표 안 · 좌표 근처 · 주소)
 */
public record RegionMatch(RegionCode code, Method method) {

    public RegionMatch {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(method, "method");
    }

    public enum Method { INSIDE_BOUNDARY, NEAR_BOUNDARY, ADDRESS }
}
