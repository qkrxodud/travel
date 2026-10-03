package com.kobi.territory.social.infra.repository;

import com.kobi.territory.social.infra.entity.FeedEntryJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FeedEntryJpaRepository extends JpaRepository<FeedEntryJpaEntity, Long> {

    boolean existsByGenerationAndRefId(int generation, String refId);

    @Query("select e from FeedEntryJpaEntity e where e.generation = :generation and e.actorId in :actors "
        + "and e.retractedAt is null and e.hiddenAt is null order by e.occurredAt desc, e.id desc")
    List<FeedEntryJpaEntity> recentOf(@Param("generation") int generation, @Param("actors") Collection<String> actors, Limit limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    /** 취소 이전(occurred_at ≤ at)이면서 회차가 취소 회차 이하(어느 쪽이든 0 = 모름이면 회차 조건 없음)인 체크인 소식만 거둔다. */
    @Query("update FeedEntryJpaEntity e set e.retractedAt = :at where e.generation = :generation and e.actorId = :actor "
        + "and e.mapId = :mapId and e.kind = 'VISIT' and e.regionCode = :code and e.retractedAt is null and e.occurredAt <= :at "
        + "and (:visitGeneration = 0 or e.visitGeneration = 0 or e.visitGeneration <= :visitGeneration)")
    int retractVisits(@Param("generation") int generation, @Param("actor") String actor, @Param("mapId") String mapId,
                      @Param("code") String code, @Param("visitGeneration") int visitGeneration, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FeedEntryJpaEntity e set e.hiddenAt = :at where e.generation = :generation and e.actorId = :actor "
        + "and e.mapId = :mapId and e.kind = 'VISIT' and e.regionCode in :codes and e.hiddenAt is null")
    int hideVisits(@Param("generation") int generation, @Param("actor") String actor, @Param("mapId") String mapId,
                   @Param("codes") Collection<String> codes, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FeedEntryJpaEntity e set e.hiddenAt = null where e.generation = :generation and e.actorId = :actor "
        + "and e.mapId = :mapId and e.kind = 'VISIT' and e.regionCode in :codes")
    int unhideVisits(@Param("generation") int generation, @Param("actor") String actor, @Param("mapId") String mapId,
                     @Param("codes") Collection<String> codes);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    /** from 개인 지도의 체크인 소식(이미 into 로 귀속된 늦은 소식 포함)을 into 개인 지도로. */
    @Query("update FeedEntryJpaEntity e set e.mapId = :intoMap, e.visitGeneration = 0 where e.generation = :generation "
        + "and e.actorId in (:from, :into) and e.mapId = :fromMap and e.kind = 'VISIT'")
    int moveMergedVisits(@Param("generation") int generation, @Param("from") String from, @Param("into") String into,
                         @Param("fromMap") String fromMap, @Param("intoMap") String intoMap);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FeedEntryJpaEntity e set e.actorId = :into, e.visitGeneration = 0 where e.generation = :generation and e.actorId = :from")
    int reassignActor(@Param("generation") int generation, @Param("from") String from, @Param("into") String into);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FeedEntryJpaEntity e where e.generation = :generation")
    int deleteGeneration(@Param("generation") int generation);
}
