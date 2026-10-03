package com.kobi.territory.sharing.domain.showcase;

/** 공개 정보의 진행 요약(레벨·칭호·스트릭·도감 테마). */
public record ShowcaseProgress(int level, String titleName, int streakMonths, int themesCompleted, int themeTotal) {
    public static ShowcaseProgress start(int themeTotal) {
        return new ShowcaseProgress(1, null, 0, 0, themeTotal);
    }
}
