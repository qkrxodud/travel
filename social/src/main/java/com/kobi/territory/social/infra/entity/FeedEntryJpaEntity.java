package com.kobi.territory.social.infra.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.domain.feed.FeedDetail;
import com.kobi.territory.social.domain.feed.FeedEntry;
import com.kobi.territory.social.domain.feed.FeedGeneration;
import com.kobi.territory.social.domain.feed.FeedKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * feed_entry — 친구 소식 읽기 모델 한 행. generation = 세대(재구성은 다음 세대에 쌓고 바꾼다), (generation, ref_id) UNIQUE(원본 이벤트 멱등 키), payload = 소식 내용 JSON(FeedDetail).
 * region_code 는 탈퇴 숨김·복구가 지역으로 고르려고 payload 밖에도 둔다. retracted_at = 체크인 취소로 거둠, hidden_at = 탈퇴 유예 숨김
 * (행은 남긴다 — 같은 이벤트가 다시 와도 되살아나지 않게).
 */
@Entity
@Table(name = "feed_entry")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedEntryJpaEntity {

    private static final ObjectMapper JSON = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int generation;

    @Column(name = "ref_id", nullable = false, length = 160)
    private String refId;

    @Column(name = "actor_id", nullable = false, length = 36)
    private String actorId;

    @Column(name = "map_id", length = 36)
    private String mapId;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(nullable = false, length = 1000)
    private String payload;

    /** 체크인 회차(0 = 모름 — 다른 종류·예전 이벤트·병합으로 옮긴 소식). */
    @Column(name = "visit_generation", nullable = false)
    private int visitGeneration;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "retracted_at")
    private Instant retractedAt;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    public static FeedEntryJpaEntity from(FeedGeneration generation, FeedEntry entry) {
        FeedEntryJpaEntity entity = new FeedEntryJpaEntity();
        entity.generation = generation.value();
        entity.refId = entry.refId();
        entity.actorId = entry.actorId().value();
        entity.mapId = entry.mapId();
        entity.kind = entry.kind().name();
        entity.regionCode = entry.detail().regionCode();
        entity.payload = write(entry.detail());
        entity.occurredAt = entry.occurredAt();
        entity.visitGeneration = entry.visitGeneration();
        return entity;
    }

    public FeedEntry toDomain() {
        return new FeedEntry(refId, ExplorerId.of(actorId), mapId, FeedKind.valueOf(kind), read(payload), occurredAt, visitGeneration);
    }

    private static String write(FeedDetail detail) {
        try {
            return JSON.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("소식 내용 직렬화 실패: " + detail, exception);
        }
    }

    private static FeedDetail read(String payload) {
        try {
            return JSON.readValue(payload, FeedDetail.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("소식 내용 역직렬화 실패: " + payload, exception);
        }
    }
}
