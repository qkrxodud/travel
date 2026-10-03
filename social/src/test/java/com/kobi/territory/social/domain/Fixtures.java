package com.kobi.territory.social.domain;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.domain.friendship.Friendship;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 소셜 테스트의 등장인물·지도·시각과 준비 문장(Spring 없음).
 * <p>
 * 팔로우 관계는 {@code follows(ME, KIM)}("나는 김을 팔로우한다")로 적는다.
 */
public final class Fixtures {

    public static final ExplorerId ME = ExplorerId.of("00000000-0000-0000-0000-00000000000a");
    public static final ExplorerId KIM = ExplorerId.of("00000000-0000-0000-0000-00000000000b");
    public static final ExplorerId LEE = ExplorerId.of("00000000-0000-0000-0000-00000000000c");
    public static final ExplorerId PARK = ExplorerId.of("00000000-0000-0000-0000-00000000000d");
    /** 계정으로 합쳐진 익명 탐험가 */
    public static final ExplorerId ANON = ExplorerId.of("00000000-0000-0000-0000-0000000000ee");

    public static final String PERSONAL_MAP = "00000000-0000-0000-0000-000000000101";
    public static final String SHARED_MAP = "00000000-0000-0000-0000-000000000202";

    public static final Instant T0 = Instant.parse("2026-10-03T03:00:00Z");
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private Fixtures() {}

    /** follower 가 followee 를 팔로우한다. */
    public static Friendship follows(ExplorerId follower, ExplorerId followee) {
        return Friendship.restore(follower, followee, T0);
    }
}
