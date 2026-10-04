package com.kobi.territory.progression.domain.collectionbook;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.List;

/**
 * 계절 회차가 이번 체크인(또는 복구)으로 처음 완성됨 → application 이 수령자마다 SeasonCompleted(공개 이벤트)로 적재한다.
 *
 * @param recipients 완성 시점 지도 멤버 전원(+150 XP·계절 칭호·회차 배경 수령자 — 테마와 같은 결정 1)
 */
public record SeasonCompletion(String mapId, String roundId, ExplorerId completedBy, Instant completedAt,
                               List<ExplorerId> recipients) {
    public SeasonCompletion {
        recipients = List.copyOf(recipients);
    }

    public boolean rewards(ExplorerId explorer) {
        return recipients.contains(explorer);
    }

    public String seasonId() {
        return SeasonRound.seasonOf(roundId);
    }
}
