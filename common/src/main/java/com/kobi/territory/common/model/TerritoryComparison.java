package com.kobi.territory.common.model;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 두 탐험가 영토의 집합 비교(VS) — 나만 · 둘 다 · 상대만 간 지역(5단계, 공유 커널). 공유의 VS 카드(4단계)와 소셜의 영토 비교가
 * 같은 계산을 쓴다(중복 구현 금지 — 두 컨텍스트는 서로를 참조할 수 없으므로 공유 커널에 둔다). 입력은 지역 코드 집합(어떤 영토를
 * 넣을지는 호출자가 정한다), 결과 목록은 코드 순으로 정렬해 불변으로 낸다.
 * 내부 표현(정렬 집합)을 숨기고 팩토리를 강제하므로 class(class vs record 기준).
 */
public final class TerritoryComparison {

    private final List<String> onlyMine;
    private final List<String> both;
    private final List<String> onlyTheirs;

    private TerritoryComparison(List<String> onlyMine, List<String> both, List<String> onlyTheirs) {
        this.onlyMine = List.copyOf(onlyMine);
        this.both = List.copyOf(both);
        this.onlyTheirs = List.copyOf(onlyTheirs);
    }

    /** 내 지역 코드 · 상대 지역 코드 → 비교. 같은 코드가 여러 번 와도 한 번으로 센다. */
    public static TerritoryComparison of(Collection<String> mine, Collection<String> theirs) {
        Set<String> mineSet = new TreeSet<>(Objects.requireNonNull(mine, "mine"));
        Set<String> theirSet = new TreeSet<>(Objects.requireNonNull(theirs, "theirs"));
        return new TerritoryComparison(
            mineSet.stream().filter(code -> !theirSet.contains(code)).toList(),
            mineSet.stream().filter(theirSet::contains).toList(),
            theirSet.stream().filter(code -> !mineSet.contains(code)).toList());
    }

    public List<String> onlyMine() { return onlyMine; }
    public List<String> both() { return both; }
    public List<String> onlyTheirs() { return onlyTheirs; }

    public int onlyMineCount() { return onlyMine.size(); }
    public int bothCount() { return both.size(); }
    public int onlyTheirsCount() { return onlyTheirs.size(); }

    /** 내 영토 수(나만 + 둘 다). */
    public int mineCount() {
        return onlyMine.size() + both.size();
    }

    /** 상대 영토 수(상대만 + 둘 다). */
    public int theirsCount() {
        return onlyTheirs.size() + both.size();
    }

    /** 내가 앞선 지역 수(음수면 뒤짐). */
    public int lead() {
        return mineCount() - theirsCount();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TerritoryComparison comparison && onlyMine.equals(comparison.onlyMine)
            && both.equals(comparison.both) && onlyTheirs.equals(comparison.onlyTheirs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(onlyMine, both, onlyTheirs);
    }

    @Override
    public String toString() {
        return "TerritoryComparison[onlyMine=" + onlyMine.size() + ", both=" + both.size() + ", onlyTheirs=" + onlyTheirs.size() + "]";
    }
}
