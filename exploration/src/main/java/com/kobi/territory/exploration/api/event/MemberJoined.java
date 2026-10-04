package com.kobi.territory.exploration.api.event;

import com.kobi.territory.common.event.DomainEvent;
import java.time.Instant;

/**
 * 지도 합류(초대코드·프로필 링크). 꾸미기가 이미 완성된 테마 보상(세트 배경)을 새 멤버에게 지급한다(XP·칭호 없음).
 *
 * @param role      OWNER | MEMBER
 * @param rejoined  탈퇴 유예 안에 돌아온 재가입 — 숨긴 방문은 탐험이 복구하고 VisitsRestored 를 낸다(선점은 돌아오지 않음)
 * @param invitedBy 초대한 탐험가(4단계, 초대 보상) — 초대코드 합류는 그 시점 지도장, 프로필 링크 합류는 프로필 주인.
 *                  지도 생성·개인 지도·병합 대체 합류처럼 초대가 아닌 합류와 필드가 없던 예전 이벤트는 null(보상 없음).
 *                  재가입(rejoined)은 값이 있어도 보상 대상이 아니다(꾸미기가 판단).
 * @param joinedVia 초대 합류의 경로(10단계, 분석의 초대 경로 구분): {@value #VIA_INVITE_CODE} | {@value #VIA_PROFILE_LINK}.
 *                  초대가 아닌 합류와 필드가 없던 예전 이벤트는 null.
 */
public record MemberJoined(String mapId, String explorerId, String role, Instant joinedAt, boolean rejoined, String invitedBy,
                           String joinedVia) implements DomainEvent {

    public static final String VIA_INVITE_CODE = "INVITE_CODE";
    public static final String VIA_PROFILE_LINK = "PROFILE_LINK";

    /** 경로 없이 초대자만(10단계 이전 호출부와 하위 호환). */
    public MemberJoined(String mapId, String explorerId, String role, Instant joinedAt, boolean rejoined, String invitedBy) {
        this(mapId, explorerId, role, joinedAt, rejoined, invitedBy, null);
    }

    /** 초대가 아닌 합류(지도 생성의 지도장, 병합 대체 합류 등) — invitedBy 없음. 4단계 이전 호출부와 하위 호환. */
    public MemberJoined(String mapId, String explorerId, String role, Instant joinedAt, boolean rejoined) {
        this(mapId, explorerId, role, joinedAt, rejoined, null, null);
    }

    @Override
    public Instant occurredAt() {
        return joinedAt;
    }
}
