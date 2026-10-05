package com.kobi.territory.catalog.domain.lineup;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 바깥 자료(축제·관광지) 읽기 결과 — 읽었거나({@link Fetched}) 못 읽었거나({@link Failed}). */
public sealed interface FestivalFetch {

    /** 여러 번 읽은 결과를 하나로 — 하나라도 못 읽었으면 그 실패(앞의 것), 아니면 합친 자료(가장 이른 조회 시각, 읽다 만 것이 있으면 표시). */
    static FestivalFetch combine(FestivalFetch first, FestivalFetch second) {
        if (first instanceof Failed) return first;
        if (second instanceof Failed) return second;
        Fetched left = (Fetched) first;
        Fetched right = (Fetched) second;
        List<Festival> festivals = new ArrayList<>(left.festivals());
        festivals.addAll(right.festivals());
        List<Attraction> attractions = new ArrayList<>(left.attractions());
        attractions.addAll(right.attractions());
        Instant fetchedAt = left.fetchedAt().isBefore(right.fetchedAt()) ? left.fetchedAt() : right.fetchedAt();
        return new Fetched(festivals, attractions, fetchedAt, left.truncated() || right.truncated());
    }

    /**
     * @param attractions 계절 관광지(키워드 검색)
     * @param fetchedAt   자료를 기관에서 읽은 시각(응답 캐시에서 꺼냈으면 그때 읽은 시각)
     * @param truncated   쪽 수 상한 때문에 뒤쪽을 읽지 않았는지
     */
    record Fetched(List<Festival> festivals, List<Attraction> attractions, Instant fetchedAt, boolean truncated) implements FestivalFetch {
        public Fetched {
            festivals = List.copyOf(festivals);
            attractions = List.copyOf(attractions);
            Objects.requireNonNull(fetchedAt, "fetchedAt");
        }

        /** 축제만. */
        public Fetched(List<Festival> festivals, Instant fetchedAt, boolean truncated) {
            this(festivals, List.of(), fetchedAt, truncated);
        }
    }

    /** @param detail 운영자에게 보일 짧은 설명(키 값은 넣지 않는다, 없으면 빈 문자열) */
    record Failed(FetchFailure failure, String detail) implements FestivalFetch {
        public Failed {
            Objects.requireNonNull(failure, "failure");
            detail = detail == null ? "" : detail;
        }

        public String message() {
            return detail.isBlank() ? failure.message() : failure.message() + " — " + detail;
        }
    }
}
