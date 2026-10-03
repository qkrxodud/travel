package com.kobi.territory.sharing.domain.showcase;

import com.kobi.territory.common.model.TerritoryComparison;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 일급 컬렉션: 한 탐험가의 공개용 방문(개인 지도). 정확한 방문일은 안에서 순서·연도 집계에만 쓰고 밖으로는 월 단위
 * {@link PublicVisit}·집계 값만 낸다(§7). 메모·사진은 처음부터 들고 있지 않다. 카탈로그에 없는 지역(폐지 등)은 뺀다.
 */
public final class PublicVisits {

    private static final Comparator<Entry> LATEST_FIRST = Comparator.comparing(Entry::visitDate)
        .thenComparing(Entry::visitedAt).reversed();

    private static final Comparator<Entry> BY_REGION_CODE = Comparator.comparing(entry -> entry.region().code());

    private final List<Entry> entries;

    private PublicVisits(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static PublicVisits empty() {
        return new PublicVisits(List.of());
    }

    /** 방문 사실(처리 시각 순서와 무관하게 받아도 된다) → 처리 시각 순 nth 를 매긴다. 같은 지역이 두 번 오면 첫 것만. */
    public static PublicVisits of(Collection<VisitFact> facts, RegionAtlas atlas) {
        Map<String, VisitFact> firstByRegion = new LinkedHashMap<>();
        facts.stream().sorted(Comparator.comparing(VisitFact::visitedAt))
            .forEach(fact -> firstByRegion.putIfAbsent(fact.regionCode(), fact));
        List<Entry> entries = new ArrayList<>();
        firstByRegion.values().forEach(fact -> atlas.region(fact.regionCode()).ifPresent(region ->
            entries.add(new Entry(region, fact.visitDate(), fact.visitedAt(), entries.size() + 1))));
        return new PublicVisits(entries);
    }

    public int count() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 칠한 지역 코드(지도 색칠). */
    public Set<String> paintedCodes() {
        return entries.stream().map(entry -> entry.region().code()).collect(Collectors.toUnmodifiableSet());
    }

    /** 칠한 전설 지역 코드(지도의 전설 표시). */
    public Set<String> legendCodes() {
        return entries.stream().filter(entry -> entry.region().legend()).map(entry -> entry.region().code())
            .collect(Collectors.toUnmodifiableSet());
    }

    public int legendCount() {
        return (int) entries.stream().filter(entry -> entry.region().legend()).count();
    }

    /** 전국 정복률(%) — 반올림. 분모가 없으면 0. */
    public int conquestPercent(RegionAtlas atlas) {
        return atlas.regionCount() == 0 ? 0 : Math.round(100f * count() / atlas.regionCount());
    }

    /** 모든 지역을 칠한 시·도 수. */
    public int conqueredProvinceCount(RegionAtlas atlas) {
        Map<String, Long> byProvince = countByProvince(entries);
        return (int) atlas.provincesInOrder().stream()
            .filter(province -> province.regionCount() > 0
                && byProvince.getOrDefault(province.code(), 0L) >= province.regionCount())
            .count();
    }

    /** 가장 최근 방문(방문일 → 처리 시각 순). */
    public Optional<PublicVisit> mostRecent() {
        return entries.stream().sorted(LATEST_FIRST).findFirst().map(Entry::toPublic);
    }

    /** 최근 방문 limit 개(방문일 늦은 순, 월 단위로만 공개). */
    public List<PublicVisit> latest(int limit) {
        return entries.stream().sorted(LATEST_FIRST).limit(limit).map(Entry::toPublic).toList();
    }

    /** 방문일이 그 해인 지역 코드(리캡 지도). */
    public Set<String> codesVisitedIn(Year year) {
        return entries.stream().filter(entry -> entry.in(year)).map(entry -> entry.region().code())
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 연간 리캡(프로토타입 recap()과 같은 규칙, 방문일 기준) — 카드 PNG·리캡 JSON 이 함께 쓰는 유일한 계산(06 QA P2-1).
     * 동점: 가장 많이 간 시·도·가장 희귀한 곳은 지역 코드가 작은 쪽(프로토타입은 지역 코드 순으로 돌았다), 가장 바쁜 달은 이른 달.
     */
    public YearRecap recap(Year year) {
        List<Entry> inYear = entries.stream().filter(entry -> entry.in(year)).sorted(BY_REGION_CODE).toList();
        int[] months = new int[12];
        inYear.forEach(entry -> months[entry.visitDate().getMonthValue() - 1]++);
        YearRecap.ProvinceTally topProvince = topProvinceOf(inYear);
        RegionInfo rarest = inYear.stream()
            .max(Comparator.comparing((Entry entry) -> entry.region().rarity()).thenComparing(BY_REGION_CODE.reversed()))
            .map(Entry::region).orElse(null);
        Map<String, List<Entry>> byProvince = entries.stream()
            .collect(Collectors.groupingBy(entry -> entry.region().provinceCode()));
        int newProvinces = (int) byProvince.values().stream()
            .filter(visits -> visits.stream().allMatch(entry -> entry.in(year))).count();
        int busiestIndex = 0;
        for (int i = 1; i < months.length; i++) {
            if (months[i] > months[busiestIndex]) busiestIndex = i;
        }
        YearRecap.MonthTally busiest = months[busiestIndex] == 0 ? null
            : new YearRecap.MonthTally(busiestIndex + 1, months[busiestIndex]);
        List<Integer> monthCounts = Arrays.stream(months).boxed().toList();
        return new YearRecap(year.getValue(), inYear.size(), monthCounts, topProvince, rarest, newProvinces, busiest);
    }

    /** 가장 많이 간 시·도 — 동점이면 그 해 방문 중 가장 작은 지역 코드를 가진 시·도(inYear 는 지역 코드 순). */
    private static YearRecap.ProvinceTally topProvinceOf(List<Entry> inYearByCode) {
        Map<String, Long> counts = countByProvince(inYearByCode);
        long most = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        return counts.entrySet().stream().filter(count -> count.getValue() == most).findFirst()
            .map(top -> new YearRecap.ProvinceTally(top.getKey(), provinceNameOf(inYearByCode, top.getKey()),
                Math.toIntExact(top.getValue())))
            .orElse(null);
    }

    /**
     * 요약 해시용 정규 표현(지역·방문일·순서). 정확한 방문일이 들어가지만 밖으로는 해시로만 나간다(최근 여행 순서·리캡 연도가
     * 방문일에 달려 있어 해시에 넣어야 바뀜을 잡는다).
     */
    String fingerprint() {
        return entries.stream().map(entry -> entry.region().code() + ":" + entry.visitDate() + ":" + entry.nth())
            .collect(Collectors.joining(","));
    }

    /** 두 탐험가 비교(VS): 나만 · 둘 다 · 상대만 간 곳 — 계산은 소셜 영토 비교와 같은 공유 커널 함수(5단계, 중복 구현 금지). */
    public VersusTally versus(PublicVisits other) {
        return VersusTally.of(TerritoryComparison.of(paintedCodes(), other.paintedCodes()));
    }

    private static Map<String, Long> countByProvince(List<Entry> visits) {
        return visits.stream().collect(Collectors.groupingBy(entry -> entry.region().provinceCode(), LinkedHashMap::new,
            Collectors.counting()));
    }

    private static String provinceNameOf(List<Entry> visits, String provinceCode) {
        return visits.stream().filter(entry -> entry.region().provinceCode().equals(provinceCode)).findFirst()
            .map(entry -> entry.region().provinceName()).orElse(provinceCode);
    }

    /** 안에서만 쓰는 정확한 방문일(밖으로는 월 단위). */
    private record Entry(RegionInfo region, LocalDate visitDate, Instant visitedAt, int nth) {
        boolean in(Year year) {
            return visitDate.getYear() == year.getValue();
        }

        PublicVisit toPublic() {
            return new PublicVisit(region, VisitMonth.of(visitDate), nth);
        }
    }
}
