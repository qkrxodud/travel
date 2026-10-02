package com.kobi.territory.exploration.application;

import com.kobi.territory.exploration.domain.map.ExpeditionMap;
import com.kobi.territory.exploration.domain.map.Member;

/** 요청한 탐험가가 대상 지도의 멤버임을 확인한 결과. */
public record MapMembership(ExpeditionMap map, Member member) {}
