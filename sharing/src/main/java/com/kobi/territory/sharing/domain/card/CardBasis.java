package com.kobi.territory.sharing.domain.card;

import java.util.Objects;

/**
 * 카드가 그려질 때의 기준(QA P2-1·P2-2 수정): 실제로 그리는 공개 요약(Showcase + 카드 종류 + 기준 연도)의 해시와, 카드에 찍힌
 * handle. 이벤트를 놓쳐도(재계산·병합처럼 이벤트 없이 바뀐 진행·가방) 요약이 바뀌면 해시가 바뀌므로 무효화가 빠지지 않는다.
 *
 * @param summaryHash 공개 요약 해시(SHA-256 hex, Showcase.summaryHash)
 * @param handle      카드에 찍힌 공개 handle, 익명이면 null
 */
public record CardBasis(String summaryHash, String handle) {
    public CardBasis {
        Objects.requireNonNull(summaryHash, "summaryHash");
        if (summaryHash.isBlank()) throw new IllegalArgumentException("빈 요약 해시");
    }

    /** handle(신원)이 다른지 — 익명 → 계정, handle 변경. */
    public boolean identityDiffers(CardBasis other) {
        return !Objects.equals(handle, other.handle);
    }

    /** 이미지 키에 넣는 짧은 해시(같은 해시의 이미지만 그 기준으로 기록된다 — QA P3-4). */
    public String shortHash() {
        return summaryHash.substring(0, Math.min(16, summaryHash.length()));
    }
}
