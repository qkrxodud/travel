package com.kobi.territory.exploration.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.time.LocalDate;

/** @param mapId null이면 개인 지도 */
public record CheckInCommand(ExplorerId explorerId, String mapId, RegionCode regionCode, LocalDate visitDate,
                             String memo, String photoUrl) {}
