package com.kobi.territory.progression.domain.policy;

import java.util.List;

/** 뱃지 조건(카탈로그 badges.json 의 condition 을 application 이 변환해 넣는다). 판정은 순수 함수. */
public sealed interface BadgeRule {

    boolean satisfiedBy(BadgeFacts facts);

    record RegionCount(int min) implements BadgeRule {
        public boolean satisfiedBy(BadgeFacts facts) { return facts.regions() >= min; }
    }

    record LegendCount(int min) implements BadgeRule {
        public boolean satisfiedBy(BadgeFacts facts) { return facts.legend() >= min; }
    }

    record ProvincesComplete(List<String> provinces) implements BadgeRule {
        public ProvincesComplete {
            provinces = List.copyOf(provinces);
        }

        public boolean satisfiedBy(BadgeFacts facts) { return provinces.stream().allMatch(facts::complete); }
    }

    /** 그룹마다 1곳 이상(예: 충청·전라·경상 각 1곳). */
    record ProvinceGroupsTouched(List<List<String>> groups) implements BadgeRule {
        public ProvinceGroupsTouched {
            groups = groups.stream().map(List::copyOf).toList();
        }

        public boolean satisfiedBy(BadgeFacts facts) {
            return groups.stream().allMatch(group -> group.stream().anyMatch(province -> facts.in(province) > 0));
        }
    }

    record AllProvincesTouched() implements BadgeRule {
        public boolean satisfiedBy(BadgeFacts facts) {
            return !facts.provinceTotals().isEmpty()
                && facts.provinceTotals().keySet().stream().allMatch(province -> facts.in(province) > 0);
        }
    }

    record ThemesCompleted(int min) implements BadgeRule {
        public boolean satisfiedBy(BadgeFacts facts) { return facts.themesCompleted() >= min; }
    }

    record StreakMonths(int min) implements BadgeRule {
        public boolean satisfiedBy(BadgeFacts facts) { return facts.streakMonths() >= min; }
    }

    /** 이번 주 미스터리 보너스를 받은 주가 min 번 이상(8단계 "미스터리 탐험가"). */
    record MysteryFound(int min) implements BadgeRule {
        public boolean satisfiedBy(BadgeFacts facts) { return facts.mysteryFound() >= min; }
    }

    /** 재방문 도장이 min 개 이상(9단계 "단골 여행자"). */
    record RevisitStamps(int min) implements BadgeRule {
        public boolean satisfiedBy(BadgeFacts facts) { return facts.revisitStamps() >= min; }
    }

    /** 가고 싶은 곳을 min 곳 이상 다녀옴(9단계 "꿈을 이룬 여행자"). */
    record WishesFulfilled(int min) implements BadgeRule {
        public boolean satisfiedBy(BadgeFacts facts) { return facts.wishesFulfilled() >= min; }
    }

    record ConquestRatio(double ratio) implements BadgeRule {
        public boolean satisfiedBy(BadgeFacts facts) {
            return facts.totalRegions() > 0 && facts.regions() >= ratio * facts.totalRegions();
        }
    }
}
