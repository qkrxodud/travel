package com.kobi.territory.catalog.domain.lineup;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 마지막 수집 시도 — 관리자 화면 경고의 근거.
 *
 * @param warnings 실패 까닭 또는 모을 때 난 경고
 */
public record CollectionAttempt(Instant at, Outcome outcome, List<String> warnings) {

    public CollectionAttempt {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(outcome, "outcome");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean failed() {
        return outcome.failed();
    }

    /** 시도 결과. code 는 API 계약 값(이름 그대로). */
    public enum Outcome {
        /** 회차 지역 수만큼 TourAPI 근거 지역을 모았다. */
        COLLECTED,
        /** 모았지만 근거 지역이 모자라 AI 추정으로 채웠다. */
        PARTIAL,
        NOT_CONFIGURED,
        KEY_REJECTED,
        QUOTA_EXCEEDED,
        BAD_RESPONSE,
        UNREACHABLE;

        public boolean failed() {
            return this != COLLECTED && this != PARTIAL;
        }

        static Outcome of(FetchFailure failure) {
            return valueOf(failure.name());
        }
    }
}
