package com.kobi.territory.catalog.api;

import com.kobi.territory.common.model.Rarity;
import java.time.LocalDate;

public record RegionView(
    String code,
    String name,
    String provinceCode,
    String provinceName,
    Rarity rarity,
    String countryCode,
    int version,
    String replacedBy,
    LocalDate retiredAt
) {}
