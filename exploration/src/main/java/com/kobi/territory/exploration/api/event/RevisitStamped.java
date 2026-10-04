package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 재방문 도장을 받았다(9단계). 이미 칠한 지역(탐험가 단위 활성 방문)에 그 지역을 처음 칠한 해보다 뒤의 해에 "다시 다녀왔어요"를 누르면
 * 그 해 도장 하나(지역·연도당 한 번). 영토·선점·정복률은 바뀌지 않고, 도장은 지우지 않는다(취소 없음).
 * 하류: 진행(XP·뱃지 "단골 여행자"), 꾸미기(그 지역 특산물의 2회차 색 변형), 소셜(새 소식). outbox aggregate = ("StampBook", explorerId).
 *
 * @param year        도장 연도(처리 시각의 서비스 시간대 연도)
 * @param firstYear   그 지역을 처음 칠한 해(지금 남아 있는 방문 중 가장 이른 처리 시각의 연도)
 * @param stampCount  이 도장을 포함한 이 탐험가의 도장 수
 */
public record RevisitStamped(String explorerId, String regionCode, String provinceCode, int year, int firstYear, int stampCount,
                             Instant stampedAt) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return stampedAt;
    }
}
