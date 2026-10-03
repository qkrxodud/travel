package com.kobi.territory.sharing.domain.privacy;

import com.kobi.territory.sharing.domain.SharingError;
import java.util.Arrays;
import java.util.Locale;

/**
 * 공개 프로필 공개 범위(§7 — 전체·친구·비공개). 5단계부터 FRIENDS 가 실제로 동작한다: 서로 팔로우한 친구(맞팔로우, 소셜
 * Friendship)에게만 프로필·카드·VS 가 열리고, 그 밖의 사람에게는 PRIVATE 와 같은 404(존재 숨김)다.
 */
public enum ProfileVisibility {
    PUBLIC, FRIENDS, PRIVATE;

    /** 로그인하지 않은 사람(누구나)에게 보이는지. */
    public boolean visibleToPublic() {
        return this == PUBLIC;
    }

    /** 보는 사람에게 보이는지 — PUBLIC 은 누구나, FRIENDS 는 주인과 서로 팔로우한 친구만, PRIVATE 는 아무도. */
    public boolean visibleTo(boolean mutualFriend) {
        return this == PUBLIC || (this == FRIENDS && mutualFriend);
    }

    /** 요청 값 → 공개 범위. 모르는 값·빈 값이면 INVALID_VISIBILITY. */
    public static ProfileVisibility parse(String raw) {
        String normalized = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(value -> value.name().equals(normalized)).findFirst()
            .orElseThrow(() -> SharingError.INVALID_VISIBILITY.exception(String.valueOf(raw)));
    }
}
