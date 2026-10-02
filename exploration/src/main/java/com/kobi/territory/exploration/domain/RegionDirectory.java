package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.model.RegionCode;
import java.util.Optional;

/** 지역 정보 조회 포트. 구현은 application 계층(카탈로그 Query 어댑터). 폐지된 지역도 찾을 수 있어야 한다. */
public interface RegionDirectory {
    Optional<RegionSnapshot> find(RegionCode code);

    default RegionSnapshot require(RegionCode code) {
        return find(code).orElseThrow(() -> ExplorationError.REGION_NOT_FOUND.exception(code.value()));
    }
}
