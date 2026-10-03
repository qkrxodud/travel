package com.kobi.territory.sharing.domain.showcase;

import com.kobi.territory.sharing.domain.SharingError;
import java.time.Year;
import java.util.List;
import java.util.Objects;

/**
 * 연간 리캡(방문일 기준 그 해 — 월 단위 집계만). 계산은 {@link PublicVisits#recap(Year)} 한 곳 — 자랑 카드 PNG(개인 지도)와
 * 리캡 JSON(GET /me/recap, 지도 선택 가능)이 같은 값을 쓴다(06 QA P2-1). 문장("서울 3곳")은 여기서 만들지 않는다 — 카드 문구는
 * {@link CardComposer}, 화면 문구는 화면 몫. 숫자·순위·동점 규칙만 여기서 정한다.
 * <p>
 * 동점 규칙(프로토타입·기존 화면과 같음): 가장 많이 간 시·도·가장 희귀한 곳은 지역 코드가 작은 쪽, 가장 바쁜 달은 이른 달.
 *
 * @param newRegions    그 해에 방문일이 있는 영토 수
 * @param monthCounts   1~12월 영토 수(12칸)
 * @param topProvince   가장 많이 간 시·도, 그 해 방문이 없으면 null
 * @param rarest        가장 희귀한 곳, 그 해 방문이 없으면 null
 * @param newProvinces  그 해에 처음 밟은 시·도 수(그 시·도의 방문이 모두 그 해)
 * @param busiestMonth  가장 바쁜 달, 그 해 방문이 없으면 null
 */
public record YearRecap(int year, int newRegions, List<Integer> monthCounts, ProvinceTally topProvince, RegionInfo rarest,
                        int newProvinces, MonthTally busiestMonth) {

    /** 리캡으로 고를 수 있는 연도 범위(달력 연도 — 게임 규칙 값이 아니다). */
    private static final int MIN_YEAR = 1;
    private static final int MAX_YEAR = 9999;

    public YearRecap {
        monthCounts = List.copyOf(monthCounts);
        if (monthCounts.size() != 12) throw new IllegalArgumentException("monthCounts 는 12칸");
    }

    /** 요청 연도(생략이면 올해). 범위 밖이면 400 INVALID_YEAR. */
    public static Year yearOf(Integer requested, Year current) {
        Objects.requireNonNull(current, "current");
        if (requested == null) return current;
        if (requested < MIN_YEAR || requested > MAX_YEAR) throw SharingError.INVALID_YEAR.exception(requested);
        return Year.of(requested);
    }

    public int maxMonthCount() {
        return monthCounts.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** 시·도 하나의 그 해 영토 수. */
    public record ProvinceTally(String provinceCode, String provinceName, int count) {
        public ProvinceTally {
            Objects.requireNonNull(provinceCode, "provinceCode");
            Objects.requireNonNull(provinceName, "provinceName");
        }
    }

    /** 한 달(1~12)의 영토 수. */
    public record MonthTally(int month, int count) {
        public MonthTally {
            if (month < 1 || month > 12) throw new IllegalArgumentException("month=" + month);
        }
    }
}
