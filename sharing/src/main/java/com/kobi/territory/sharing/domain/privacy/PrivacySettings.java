package com.kobi.territory.sharing.domain.privacy;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.sharing.domain.SharingError;
import java.time.Instant;
import java.util.Objects;

/**
 * 공개 범위 설정 애그리거트(explorerId). 공개 프로필(/u/{handle})·자랑 카드 링크를 누가 볼 수 있는지 정한다.
 * 기본값 PRIVATE(사용자 결정 Q1) — 사용자가 프로필 탭에서 "공개하기"를 켜야 /u/{handle} 이 열린다. 무엇을 보여 주는지(색칠·집계만, 메모·사진 비공개, 날짜 월 단위)는 설정과 무관하게 고정이다(§7).
 */
public final class PrivacySettings {

    public static final ProfileVisibility DEFAULT = ProfileVisibility.PRIVATE;

    private final ExplorerId explorerId;
    private ProfileVisibility visibility;
    private Instant updatedAt;

    private PrivacySettings(ExplorerId explorerId, ProfileVisibility visibility, Instant updatedAt) {
        this.explorerId = Objects.requireNonNull(explorerId, "explorerId");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.updatedAt = updatedAt;
    }

    /** 아직 바꾼 적 없는 설정(저장 전 — updatedAt 없음). */
    public static PrivacySettings defaults(ExplorerId explorerId) {
        return new PrivacySettings(explorerId, DEFAULT, null);
    }

    public static PrivacySettings restore(ExplorerId explorerId, ProfileVisibility visibility, Instant updatedAt) {
        return new PrivacySettings(explorerId, visibility, Objects.requireNonNull(updatedAt, "updatedAt"));
    }

    /** 공개 범위 변경. @return 실제로 바뀌었는지 */
    public boolean change(ProfileVisibility next, Instant at) {
        Objects.requireNonNull(next, "visibility");
        Objects.requireNonNull(at, "at");
        boolean changed = next != visibility;
        visibility = next;
        updatedAt = at;
        return changed;
    }

    /** 로그인하지 않은 사람에게 프로필·카드를 보여 주는지(FRIENDS 는 5단계 전까지 아니다). */
    public boolean visibleToPublic() {
        return visibility.visibleToPublic();
    }

    /** 공개 경로(/u/{handle}·카드)의 문지기: 공개가 아니면 PROFILE_NOT_FOUND — 비공개 프로필은 존재 여부도 숨긴다. */
    public void requireVisibleToPublic() {
        if (!visibleToPublic()) throw SharingError.PROFILE_NOT_FOUND.exception();
    }

    public ExplorerId explorerId() { return explorerId; }
    public ProfileVisibility visibility() { return visibility; }
    public Instant updatedAt() { return updatedAt; }
}
