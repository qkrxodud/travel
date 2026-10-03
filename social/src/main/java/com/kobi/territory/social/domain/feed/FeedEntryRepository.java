package com.kobi.territory.social.domain.feed;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 친구 소식 읽기 모델 저장소(feed_entry). 투영(프로젝터)이 쓰고 피드 조회가 읽는다. 모든 연산은 세대(generation) 하나 안에서만 —
 * 실시간 투영·조회는 지금 세대, 재구성은 다음 세대. 모든 쓰기는 멱등하다(같은 세대의 같은 refId 는 한 행, 거두기·숨기기·옮기기는 몇 번
 * 해도 같은 결과).
 */
public interface FeedEntryRepository {

    /** refId 가 그 세대에 없을 때만 넣는다. @return 새로 넣었는지 */
    boolean addIfAbsent(FeedGeneration generation, FeedEntry entry);

    /**
     * 체크인 취소: 그 탐험가의 그 지도·지역 체크인 소식 중 거두지 않았고 <b>취소 이전에 일어났으며(occurred_at ≤ cancelledAt) 회차가 취소
     * 회차 이하인</b> 것을 거둔다(행은 남긴다). 취소 뒤의 재체크인 소식은 건드리지 않는다(QA r2 P2-B — 재구성과 릴레이 재전달이 겹쳐도).
     * refId 가 아니라 (탐험가, 지도, 지역)으로 찾는다 — 병합으로 지도·주인이 바뀐 소식(회차 0 = 모름)도 시각 조건만으로 거둬진다(QA P2-5).
     *
     * @param visitGeneration 취소된 방문의 회차(0 = 모름 — 회차 조건 없이 시각만)
     */
    void retractVisits(FeedGeneration generation, ExplorerId actor, String mapId, String regionCode, int visitGeneration,
                       Instant cancelledAt);

    /** 탈퇴 유예 숨김: 그 지도에서 그 탐험가의 해당 지역 체크인 소식을 숨긴다. */
    void hideVisits(FeedGeneration generation, ExplorerId actor, String mapId, Collection<String> regionCodes, Instant at);

    /** 재가입 복구: 숨겼던 체크인 소식을 다시 보인다. */
    void unhideVisits(FeedGeneration generation, ExplorerId actor, String mapId, Collection<String> regionCodes);

    /**
     * 병합(익명 from → 계정 into): from 의 소식을 into 의 것으로 옮기고, from 개인 지도의 체크인 소식은 into 개인 지도의 것으로 옮긴다
     * (방문이 into 개인 지도로 흡수되므로 — 이후 into 의 취소가 거둘 수 있게, QA P2-5). 공유 지도 소식은 지도 그대로(재귀속).
     * 옮긴 체크인 소식의 회차는 0(모름)으로 — 흡수·재귀속된 방문은 into 의 다음 회차가 되어 from 의 회차와 맞지 않는다(시각 조건만으로 거둔다).
     */
    void absorbMerged(FeedGeneration generation, ExplorerId from, ExplorerId into, String fromPersonalMapId, String intoPersonalMapId);

    /** actors 의 거두지·숨기지 않은 소식, 최근 순 최대 limit 건. */
    List<FeedEntry> recentOf(FeedGeneration generation, Collection<ExplorerId> actors, int limit);

    /** 그 세대의 행을 모두 지운다(재구성 시작 전 남은 행 정리·실패한 세대 버리기·바뀐 뒤 옛 세대 정리). @return 지운 행 수 */
    int deleteGeneration(FeedGeneration generation);
}
