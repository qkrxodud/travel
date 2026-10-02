package com.kobi.territory.progression.domain.collectionbook;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.List;

/**
 * 테마가 이번 체크인(또는 복구)으로 처음 완성됨 → application 이 수령자마다 SetCompleted(공개 이벤트, 이름 유지)로 적재한다.
 *
 * @param completedBy 마지막 지역을 칠한 탐험가
 * @param recipients  완성 시점 지도 멤버 전원(결정 1 — +100 XP·세트 칭호·세트 배경 수령자)
 */
public record ThemeCompletion(String mapId, String themeId, ExplorerId completedBy, Instant completedAt,
                              List<ExplorerId> recipients) {
    public ThemeCompletion {
        recipients = List.copyOf(recipients);
    }

    public boolean rewards(ExplorerId explorer) {
        return recipients.contains(explorer);
    }
}
