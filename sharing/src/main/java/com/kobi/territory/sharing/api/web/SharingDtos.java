package com.kobi.territory.sharing.api.web;

import java.time.Instant;
import java.util.List;

/** 공유 웹 DTO. */
public final class SharingDtos {

    private SharingDtos() {}

    /**
     * GET /me/cards — 내 카드.
     *
     * @param handle     공개 handle(익명이면 null — 공개 링크 없음)
     * @param profileUrl 공개 프로필 경로(/u/{handle}), 익명이면 null
     * @param visibility 공개 범위
     */
    public record MyCardsResponse(String handle, String profileUrl, String visibility, boolean publiclyVisible,
                                  List<CardMetaResponse> cards) {}

    /**
     * @param previewUrl 내 미리보기 PNG(/me/cards/{kind}.png — 인증 필요)
     * @param publicUrl  공개 PNG(/u/{handle}/card/{kind}.png — OG 이미지), 익명이면 null
     */
    public record CardMetaResponse(String kind, String previewUrl, String publicUrl, boolean rendered, boolean stale,
                                   Instant renderedAt) {}

    /** PUT /me/privacy. visibility = PUBLIC | FRIENDS | PRIVATE */
    public record PrivacyRequest(String visibility) {}

    /**
     * @param publiclyVisible 로그아웃 상태의 누구나 공개 프로필·카드를 볼 수 있는지(FRIENDS 는 5단계 전까지 false)
     */
    public record PrivacyResponse(String visibility, boolean publiclyVisible, Instant updatedAt) {}
}
