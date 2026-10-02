package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 지도 탈퇴(유예 시작). 탐험이 이 멤버의 방문을 숨기고(VisitsHidden) 선점을 다음 체크인 멤버에게 넘긴다(ClaimTransferred).
 * 탐험가 단위 기록(explorer_region·전체 랭킹)은 줄지 않는다(§5).
 *
 * @param purgeAfter 이 시각이 지나면 배치가 숨긴 방문을 하드 삭제한다(MemberPurged). 그 전 재가입이면 복구
 */
public record MemberLeft(String mapId, String explorerId, Instant leftAt, Instant purgeAfter) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return leftAt;
    }
}
