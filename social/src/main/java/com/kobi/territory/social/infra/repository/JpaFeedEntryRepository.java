package com.kobi.territory.social.infra.repository;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.domain.feed.FeedEntry;
import com.kobi.territory.social.domain.feed.FeedEntryRepository;
import com.kobi.territory.social.domain.feed.FeedGeneration;
import com.kobi.territory.social.infra.entity.FeedEntryJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

/**
 * 친구 소식 저장소 어댑터 — 넣기(그 세대에 refId 가 없을 때만)와 한 문장 갱신(거두기·숨기기·복구·병합 옮기기). 모두 멱등. 빈 목록 조건
 * (IN ())은 SQL 로 보내지 않는다(저장 기술상의 처리).
 */
@Repository
class JpaFeedEntryRepository implements FeedEntryRepository {

    private final FeedEntryJpaRepository rows;

    JpaFeedEntryRepository(FeedEntryJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public boolean addIfAbsent(FeedGeneration generation, FeedEntry entry) {
        if (rows.existsByGenerationAndRefId(generation.value(), entry.refId())) return false;
        rows.save(FeedEntryJpaEntity.from(generation, entry));
        return true;
    }

    @Override
    public void retractVisits(FeedGeneration generation, ExplorerId actor, String mapId, String regionCode, int visitGeneration,
                              Instant cancelledAt) {
        rows.retractVisits(generation.value(), actor.value(), mapId, regionCode, visitGeneration, cancelledAt);
    }

    @Override
    public void hideVisits(FeedGeneration generation, ExplorerId actor, String mapId, Collection<String> regionCodes, Instant at) {
        if (regionCodes.isEmpty()) return;
        rows.hideVisits(generation.value(), actor.value(), mapId, regionCodes, at);
    }

    @Override
    public void unhideVisits(FeedGeneration generation, ExplorerId actor, String mapId, Collection<String> regionCodes) {
        if (regionCodes.isEmpty()) return;
        rows.unhideVisits(generation.value(), actor.value(), mapId, regionCodes);
    }

    /** 개인 지도 소식의 지도를 먼저 옮기고(from 기준) 주인을 바꾼다 — 두 문장 모두 몇 번 해도 같다. */
    @Override
    public void absorbMerged(FeedGeneration generation, ExplorerId from, ExplorerId into, String fromPersonalMapId,
                             String intoPersonalMapId) {
        rows.moveMergedVisits(generation.value(), from.value(), into.value(), fromPersonalMapId, intoPersonalMapId);
        rows.reassignActor(generation.value(), from.value(), into.value());
    }

    @Override
    public List<FeedEntry> recentOf(FeedGeneration generation, Collection<ExplorerId> actors, int limit) {
        if (actors.isEmpty()) return List.of();
        return rows.recentOf(generation.value(), actors.stream().map(ExplorerId::value).toList(), Limit.of(limit)).stream()
            .map(FeedEntryJpaEntity::toDomain).toList();
    }

    @Override
    public int deleteGeneration(FeedGeneration generation) {
        return rows.deleteGeneration(generation.value());
    }
}
