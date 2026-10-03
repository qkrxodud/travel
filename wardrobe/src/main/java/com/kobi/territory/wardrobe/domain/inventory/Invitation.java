package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Instant;
import java.util.Objects;

/**
 * 공유 지도 합류 한 번의 초대 사실(MemberJoined 에서 온 값). 초대 보상 판단의 입력이다.
 *
 * @param inviterId 초대한 탐험가 — 초대가 아닌 합류(지도 생성·병합 대체 합류·예전 이벤트)는 null
 * @param mapId     합류한 지도
 * @param rejoined  탈퇴 유예 안의 재가입(보상 대상 아님 — "처음 합류"만)
 * @param joinedAt  합류 시각(한정 아이템 기간 판정 기준)
 */
public record Invitation(ExplorerId inviterId, String mapId, boolean rejoined, Instant joinedAt) {
    public Invitation {
        Objects.requireNonNull(mapId, "mapId");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }
}
