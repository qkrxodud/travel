package com.kobi.territory.sharing.api.web;

import com.kobi.territory.sharing.application.MyRecap;
import com.kobi.territory.sharing.domain.showcase.YearRecap;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    /**
     * GET /me/recap — 내 연간 리캡(고른 지도에서 내가 칠한 곳, 방문일 기준 그 해). 계산은 리캡 카드 PNG 와 같다. 문장은 화면 몫.
     *
     * @param mapId         기준 지도(요청에서 생략하면 개인 지도 id)
     * @param newRegions    그 해에 방문일이 있는 영토 수
     * @param monthCounts   1~12월 영토 수(항상 12칸)
     * @param topProvince   가장 많이 간 시·도(동점이면 지역 코드가 작은 쪽), 그 해 방문이 없으면 null
     * @param rarest        가장 희귀한 곳(동점이면 지역 코드가 작은 쪽), 그 해 방문이 없으면 null
     * @param newProvinces  그 해에 처음 밟은 시·도 수(그 시·도의 내 방문이 모두 그 해)
     * @param busiestMonth  가장 바쁜 달(동점이면 이른 달), 그 해 방문이 없으면 null
     * @param setsCompleted 그 지도 도감에서 완성한 세트 수(연도 무관 누적)
     */
    public record RecapResponse(int year, String mapId, int newRegions, List<Integer> monthCounts,
                                RecapProvinceResponse topProvince, RecapRegionResponse rarest, int newProvinces,
                                RecapMonthResponse busiestMonth, int setsCompleted) {

        /** 내 리캡(연간 요약 + 기준 지도 + 완성 테마 수)을 응답으로 옮긴다. 없는 항목(최다 시·도·가장 희귀한 영토·최다 달)은 null. */
        public static RecapResponse of(MyRecap myRecap) {
            YearRecap recap = myRecap.recap();
            return new RecapResponse(recap.year(), myRecap.mapId(), recap.newRegions(), recap.monthCounts(),
                Optional.ofNullable(recap.topProvince())
                    .map(top -> new RecapProvinceResponse(top.provinceCode(), top.provinceName(), top.count())).orElse(null),
                Optional.ofNullable(recap.rarest()).map(region -> new RecapRegionResponse(region.code(), region.name(),
                    region.provinceCode(), region.provinceName(), region.rarity().name())).orElse(null),
                recap.newProvinces(),
                Optional.ofNullable(recap.busiestMonth())
                    .map(busiest -> new RecapMonthResponse(busiest.month(), busiest.count())).orElse(null),
                myRecap.setsCompleted());
        }
    }

    /** @param count 그 해 그 시·도에서 칠한 영토 수 */
    public record RecapProvinceResponse(String provinceCode, String provinceName, int count) {}

    /** @param rarity COMMON | RARE | LEGEND */
    public record RecapRegionResponse(String regionCode, String name, String provinceCode, String provinceName, String rarity) {}

    /** @param month 1~12 */
    public record RecapMonthResponse(int month, int count) {}
}
