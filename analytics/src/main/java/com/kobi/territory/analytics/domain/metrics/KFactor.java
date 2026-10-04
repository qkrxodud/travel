package com.kobi.territory.analytics.domain.metrics;

import java.util.Objects;

/**
 * K 계수(바이럴 계수) 실측 — 구간 안에 가입한 탐험가 중 다른 사람 덕분에 들어온 수 ÷ 구간 활성 탐험가 수.
 * <ul>
 *   <li>초대 유입: 가입 후 정해진 일수(기본 7) 안에 초대코드·프로필 링크로 공유 지도에 합류한 새 탐험가</li>
 *   <li>카드 유입: 이어진 방문의 첫 화면 들어온 길이 공개 카드·프로필 링크인 새 탐험가</li>
 *   <li>바이럴 신규 = 둘 중 하나라도(한 사람은 한 번만)</li>
 *   <li>활성 탐험가 = 구간 안에 이벤트가 하나라도 있는 탐험가</li>
 * </ul>
 * 1 이상이면 한 명이 한 명 넘게 데려오는 셈이다.
 */
public record KFactor(DayRange range, int invitedNewExplorers, int cardNewExplorers, int viralNewExplorers, int activeExplorers) {

    public KFactor {
        Objects.requireNonNull(range, "range");
    }

    /** 활성 탐험가가 없으면 없음. */
    public Double value() {
        return Ratio.of(viralNewExplorers, activeExplorers);
    }
}
