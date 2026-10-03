package com.kobi.territory.progression.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.domain.progress.ProvinceTally;

/** explorer_region 집계 결과 행(탐험가 × 시·도 → 활성 지역 수) — Spring Data 인터페이스 프로젝션. 변환은 여기서 한다. */
public interface ProvinceTallyRow {

    String getExplorerId();

    String getProvinceCode();

    long getRegionCount();

    default ProvinceTally toDomain() {
        return new ProvinceTally(ExplorerId.of(getExplorerId()), getProvinceCode(), Math.toIntExact(getRegionCount()));
    }
}
