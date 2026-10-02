package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExpeditionMapRepository {

    void save(ExpeditionMap map);

    Optional<ExpeditionMap> findById(MapId id);

    /**
     * 지도 행을 배타 잠금(SELECT ... FOR UPDATE, version 강제 증가)한 뒤 멤버까지 불러온다 — 지도 커맨드(합류·탈퇴·양도·설정·
     * 초대코드·유예 종료)는 반드시 이것으로 시작한다(QA P1-1: 동시 합류로 멤버 > 4, 양도·탈퇴 경합으로 OWNER 0명 방지).
     */
    Optional<ExpeditionMap> findLocked(MapId id);

    /**
     * 지도 행을 공유 잠금(SELECT … FOR SHARE)한다 — 체크인·수정·취소·이의가 territory 를 잠그기 전에 잡는다(QA P1-2). 지도 커맨드의
     * 배타 잠금과 직렬화돼, 탈퇴가 커밋된 뒤의 체크인은 멤버 확인에서 거절되고 탈퇴 전 체크인은 탈퇴 시각보다 앞선 처리 시각을 갖는다.
     * 잠금 순서는 언제나 지도 → territory. @return 지도가 있으면 true
     */
    boolean lockShared(MapId id);

    /** 탐험가 개인 지도 id(애그리거트를 불러오지 않음). */
    Optional<MapId> personalMapIdOf(ExplorerId owner);

    /** 초대코드가 가리키는 지도 id(잠그지 않음 — 이어서 findLocked). 애그리거트를 불러오지 않는다. */
    Optional<MapId> findIdByInviteCode(InviteCode code);

    /** 지금 멤버인지(애그리거트를 불러오지 않는 가벼운 확인 — 잠금 전 비멤버 거르기). */
    boolean isMember(MapId mapId, ExplorerId explorerId);

    boolean exists(MapId mapId);

    Optional<ExpeditionMap> findPersonalMap(ExplorerId owner);

    boolean existsByInviteCode(InviteCode code);

    /** 탐험가가 지금 멤버인 지도 id 전부(탈퇴 유예 중 제외). */
    List<MapId> mapIdsOf(ExplorerId explorerId);

    /** 탐험가가 지금 멤버인 지도 전부(개인 지도 먼저, 그다음 만든 순). */
    List<ExpeditionMap> mapsOf(ExplorerId explorerId);

    /** 탈퇴 유예가 now 기준으로 끝난 기록이 있는 지도 id(하드 삭제 배치용). */
    List<MapId> mapIdsWithDeparturesBefore(Instant leftBefore);

    /** selector 가 가리키는 지도(생략이면 requester 의 개인 지도). */
    default Optional<ExpeditionMap> find(MapSelector selector, ExplorerId requester) {
        return selector.personal() ? findPersonalMap(requester) : findById(selector.mapId());
    }
}
