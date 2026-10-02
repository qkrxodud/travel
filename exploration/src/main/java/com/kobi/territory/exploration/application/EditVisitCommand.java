package com.kobi.territory.exploration.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.time.LocalDate;

/** 부분 수정: null 필드는 기존 값을 유지한다. 메모를 지우려면 빈 문자열. */
public record EditVisitCommand(ExplorerId explorerId, String mapId, RegionCode regionCode, LocalDate visitDate,
                               String memo, String photoUrl) {}
