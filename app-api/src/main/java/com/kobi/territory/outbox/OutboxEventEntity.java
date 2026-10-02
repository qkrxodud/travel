package com.kobi.territory.outbox;

import com.kobi.territory.common.event.DomainEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String aggregate;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    /** 이벤트 record의 FQCN. 릴레이가 이 타입으로 역직렬화해 발행한다. */
    @Column(name = "event_type", nullable = false, length = 200)
    private String eventType;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** 공개 이벤트 한 건 → outbox 행(payload 는 호출자가 직렬화한 JSON, 이벤트 타입은 FQCN). */
    static OutboxEventEntity from(String aggregate, String aggregateId, DomainEvent event, String payload, Instant createdAt) {
        return new OutboxEventEntity(aggregate, aggregateId, event.getClass().getName(), payload, createdAt);
    }

    private OutboxEventEntity(String aggregate, String aggregateId, String eventType, String payload, Instant createdAt) {
        this.aggregate = aggregate;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    void markPublished(Instant at) {
        this.publishedAt = at;
    }

    /** 이벤트 클래스 단순 이름(예: RegionVisited). */
    public String eventName() {
        return eventType.substring(eventType.lastIndexOf('.') + 1);
    }
}
