package com.kobi.territory.exploration.domain;

/** Territory.checkIn 의 결과. application이 이것으로 RegionVisited(api)를 만든다. */
public record CheckInResult(MapId mapId, Visit visit, VisitFacts facts) {}
