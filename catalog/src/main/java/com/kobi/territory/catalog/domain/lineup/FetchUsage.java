package com.kobi.territory.catalog.domain.lineup;

/**
 * 바깥 자료 호출 현황.
 *
 * @param callsToday 오늘(서비스 시간대 날짜) 이 서비스가 한 호출 수(DB — 재기동해도 이어진다)
 * @param dailyLimit 하루 호출 상한(기관 한도보다 낮게 둔다)
 * @param settingsProblem 키를 넣었는데 키·주소 형식이 틀려 연동을 끈 까닭(값 없음 — 키가 새지 않는 문구), 없으면 null
 */
public record FetchUsage(boolean configured, int callsToday, int dailyLimit, String settingsProblem) {

    public FetchUsage(boolean configured, int callsToday, int dailyLimit) {
        this(configured, callsToday, dailyLimit, null);
    }

    /** 오늘 상한에 닿았는지 — 더는 호출하지 않는다(관리자 경고). */
    public boolean exhausted() {
        return callsToday >= dailyLimit;
    }
}
