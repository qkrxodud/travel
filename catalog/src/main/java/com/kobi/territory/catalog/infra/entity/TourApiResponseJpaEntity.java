package com.kobi.territory.catalog.infra.entity;

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
 * tourapi_response — TourAPI 응답 원문 캐시(13s단계 V11). request_key = 오퍼레이션 + 요청 변수(서비스 키는 넣지 않는다). 정상 응답만 남긴다 —
 * 같은 요청을 캐시 시간 안에 다시 하면 호출 대신 이것을 읽는다(일일 한도 보호), 회차 근거의 원문 기록도 된다.
 */
@Entity
@Table(name = "tourapi_response")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourApiResponseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_key", nullable = false, length = 300)
    private String requestKey;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "body", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String body;

    public static TourApiResponseJpaEntity of(String requestKey, Instant fetchedAt, String body) {
        TourApiResponseJpaEntity entity = new TourApiResponseJpaEntity();
        entity.requestKey = requestKey;
        entity.fetchedAt = fetchedAt;
        entity.body = body;
        return entity;
    }

    public Instant fetchedAt() {
        return fetchedAt;
    }

    public String body() {
        return body;
    }
}
