package com.kobi.territory.sharing.domain.privacy;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.sharing.domain.SharingError;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 공개 범위 설정 애그리거트(explorerId). 공개 프로필(/u/{handle})·자랑 카드 링크를 누가 볼 수 있는지 정한다.
 * FRIENDS(5단계)는 서로 팔로우한 친구에게만 열린다.
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

    /** 로그인하지 않은 사람에게 프로필·카드를 보여 주는지(PUBLIC 만). */
    public boolean visibleToPublic() {
        return visibility.visibleToPublic();
    }

    /** 공개 경로(/u/{handle}·카드)의 문지기: 공개가 아니면 PROFILE_NOT_FOUND — 비공개 프로필은 존재 여부도 숨긴다. */
    public void requireVisibleToPublic() {
        if (!visibleToPublic()) throw SharingError.PROFILE_NOT_FOUND.exception();
    }

    /**
     * viewer 에게 보이는지(5단계): 주인 본인은 공개 범위와 무관하게 항상(리더 결정 2 — 자기 프로필·카드 미리보기), 그 밖엔 PUBLIC 은
     * 누구나, FRIENDS 는 주인과 서로 팔로우한 친구만(친구 관계는 mutualFriendOfOwner 로 묻는다 — FRIENDS 일 때만 묻는다), PRIVATE 는
     * 아무도. viewer 가 없으면(익명 방문자) 친구가 아니다.
     */
    public boolean visibleTo(ExplorerId viewer, Predicate<ExplorerId> mutualFriendOfOwner) {
        if (explorerId.equals(viewer)) return true;
        if (visibility != ProfileVisibility.FRIENDS) return visibility.visibleTo(false);
        return visibility.visibleTo(viewer != null && mutualFriendOfOwner.test(viewer));
    }

    /** 공개 경로의 문지기(5단계): viewer 에게 보이지 않으면 PROFILE_NOT_FOUND(PRIVATE·친구 아님 모두 같은 응답 — 존재 숨김). */
    public void requireVisibleTo(ExplorerId viewer, Predicate<ExplorerId> mutualFriendOfOwner) {
        if (!visibleTo(viewer, mutualFriendOfOwner)) throw SharingError.PROFILE_NOT_FOUND.exception();
    }

    public ExplorerId explorerId() { return explorerId; }
    public ProfileVisibility visibility() { return visibility; }
    public Instant updatedAt() { return updatedAt; }
}
