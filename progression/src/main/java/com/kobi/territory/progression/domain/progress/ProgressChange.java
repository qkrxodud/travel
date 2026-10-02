package com.kobi.territory.progression.domain.progress;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 진행 커맨드 한 번의 결과. application 이 이것으로 공개 이벤트(LevelUp·BadgeEarned)를 outbox 에 적재한다.
 *
 * @param levelUp 레벨이 올랐으면 새 레벨(내려간 경우는 이벤트 없음)
 */
public record ProgressChange(long xpBefore, long xpAfter, Optional<Integer> levelUp, List<String> badgesEarned,
                             List<String> titlesEarned, Instant at) {

    /** 컬렉션은 방어 복사(null 불가). */
    public ProgressChange {
        Objects.requireNonNull(levelUp, "levelUp");
        badgesEarned = List.copyOf(badgesEarned);
        titlesEarned = List.copyOf(titlesEarned);
    }

    public long xpDelta() {
        return xpAfter - xpBefore;
    }
}
