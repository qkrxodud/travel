package com.kobi.territory.progression.domain.progress;

import java.util.Objects;

/**
 * 이번 달 월간 퀘스트로 받는 보호권 진행(8단계 QA P3-2 — 화면이 "몇 개"·"채웠는지"를 판정하지 않게 서버가 준다).
 *
 * @param period          그 달(yyyy-MM)
 * @param questsRewarded  보상을 받은 월간 퀘스트 수
 * @param questsRequired  모두 받아야 하는 월간 퀘스트 수
 * @param reward          모두 받으면 주는 보호권 수(설정)
 * @param earned          이번 달 몫을 받을 일이 생겼는지(모두 받음 — 상한에 막혀 0개였어도 true)
 * @param granted         이번 달 몫으로 실제로 늘어난 보호권 수(상한이면 0)
 */
public record MonthlyFreezeProgress(String period, int questsRewarded, int questsRequired, int reward, boolean earned,
                                    int granted) {
    public MonthlyFreezeProgress {
        Objects.requireNonNull(period, "period");
    }
}
