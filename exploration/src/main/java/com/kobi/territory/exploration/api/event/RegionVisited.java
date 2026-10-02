package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import com.kobi.territory.common.model.Rarity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
 * @param visitGeneration   같은 (지도, 지역, 멤버)의 체크인 회차(1부터, 3단계 결정 6). 하류는 자기가 본 회차보다 오래된 이벤트를
 *                          무시한다. 0 = 필드가 없던 예전 이벤트(알 수 없음 — 무시하지 않는다)
 * @param memberIds         체크인 시점의 지도 멤버(탈퇴 유예 중인 사람 제외, 3단계 결정 1 — 테마 완성 수령자). null = 예전 이벤트
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
    boolean isFirstClaim,
    int visitGeneration,
    List<String> memberIds
) implements DomainEvent {
    public RegionVisited {
        memberIds = memberIds == null ? null : List.copyOf(memberIds);
    }

    @Override
    public Instant occurredAt() {
        return visitedAt;
    }
}
