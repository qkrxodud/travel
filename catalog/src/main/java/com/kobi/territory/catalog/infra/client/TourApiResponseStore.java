package com.kobi.territory.catalog.infra.client;

import java.time.Instant;
import java.util.Optional;

/** TourAPI 응답 원문 캐시(tourapi_response). 요청 키에는 서비스 키를 넣지 않는다. */
public interface TourApiResponseStore {

    /** notBefore 이후에 읽은 같은 요청의 가장 최근 원문. */
    Optional<StoredResponse> latest(String requestKey, Instant notBefore);

    void save(String requestKey, Instant fetchedAt, String body);

    record StoredResponse(Instant fetchedAt, String body) {}
}
