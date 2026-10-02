package com.kobi.territory.progression.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 진행 커맨드 한 번의 결과. application 이 이것으로 공개 이벤트(LevelUp·BadgeEarned)를 outbox 에 적재한다.
 *
 * @param levelUp 레벨이 올랐으면 새 레벨(내려간 경우는 이벤트 없음)
 */
public record ProgressChange(long xpBefore, long xpAfter, Optional<Integer> levelUp, List<String> badgesEarned,
                             List<String> titlesEarned, Instant at) {

    public long xpDelta() {
        return xpAfter - xpBefore;
    }
}
