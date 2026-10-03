package com.kobi.territory.progression.domain.progress;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 진행 커맨드 한 번의 결과. application 이 이것으로 공개 이벤트(LevelUp·BadgeEarned, 8단계 StreakMilestoneReached·ProvinceConquered·
 * MysteryBonusEarned)를 outbox 에 적재한다.
 *
 * @param levelUp            레벨이 올랐으면 새 레벨(내려간 경우는 이벤트 없음)
 * @param milestonesReached  이번에 처음 닿은 연속 탐험 마일스톤(개월 수)
 * @param provincesConquered 이번에 처음 정복한 시·도 코드
 * @param mysteryFound       이번에 받은 이번 주 미스터리 보너스(그 주 사실)
 * @param freezesUsed        이번 체크인으로 빈 달을 메우느라 쓴 보호권 수
 */
public record ProgressChange(long xpBefore, long xpAfter, Optional<Integer> levelUp, List<String> badgesEarned,
                             List<String> titlesEarned, Instant at, List<Integer> milestonesReached,
                             List<String> provincesConquered, Optional<MysteryFact> mysteryFound, int freezesUsed) {

    /** 컬렉션은 방어 복사(null 불가). */
    public ProgressChange {
        Objects.requireNonNull(levelUp, "levelUp");
        Objects.requireNonNull(mysteryFound, "mysteryFound");
        badgesEarned = List.copyOf(badgesEarned);
        titlesEarned = List.copyOf(titlesEarned);
        milestonesReached = List.copyOf(milestonesReached);
        provincesConquered = List.copyOf(provincesConquered);
    }

    public long xpDelta() {
        return xpAfter - xpBefore;
    }
}
