package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import com.kobi.territory.common.model.Rarity;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 체크인 완료(공개 이벤트). Territory 커밋과 같은 트랜잭션에서 outbox에 적재된다.
 * 하류(진행·꾸미기·소셜·공유)가 영토를 다시 읽지 않도록 계산된 값을 실어 보낸다.
 *
 * @param explorerId        체크인한 탐험가(UUID)
 * @param mapId             지도(UUID)
 * @param regionCode        KR-xxxxx
 * @param provinceCode      KR-xx
 * @param visitedAt         처리 시각(서버 시계) — 스트릭·월간 퀘스트 기준
 * @param visitDate         사용자가 적은 방문일(기록용 표시 값, 진행에 영향 없음)
 * @param isFirstInProvince 이 멤버가 이 지도에서 해당 시·도 첫 방문인지
 * @param nth               이 멤버의 이 지도 기준 n번째 영토
 * @param isFirstClaim      지도 내 최초 체크인(선점)인지 — 선점 보너스 refId claim:{mapId}:{code}:{explorerId}
 */
public record RegionVisited(
    String explorerId,
    String mapId,
    String regionCode,
    Rarity rarity,
    String provinceCode,
    Instant visitedAt,
    LocalDate visitDate,
    boolean isFirstInProvince,
    int nth,
    boolean isFirstClaim
) implements DomainEvent {
    @Override
    public Instant occurredAt() {
        return visitedAt;
    }
}
