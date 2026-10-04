package com.kobi.territory.analytics.domain.journey;

/**
 * 여정 판단 기준(설정 territory.analytics.journey.*).
 *
 * @param revisitWindowDays      첫 체크인 다음 날부터 이 일수 안에 다시 오면 "재방문"(퍼널 3단계, 기본 7)
 * @param inviteAttributionDays  가입 후 이 일수 안에 초대(초대코드·프로필 링크)로 공유 지도에 합류하면 "초대 유입"(K 계수, 기본 7)
 */
public record JourneyPolicy(int revisitWindowDays, int inviteAttributionDays) {

    public JourneyPolicy {
        if (revisitWindowDays < 1 || inviteAttributionDays < 0) throw new IllegalArgumentException("여정 기준 일수가 올바르지 않다");
    }
}
