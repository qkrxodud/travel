package com.kobi.territory.social.domain.friendship;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.social.domain.SocialError;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 일급 컬렉션: 한 탐험가(me) 둘레의 팔로우 관계 — 내가 팔로우하는 사람(following)·나를 팔로우하는 사람(followers). "친구" = 서로
 * 팔로우(맞팔로우). 친구 랭킹 대상(나 + 친구), 영토 비교 허용(친구이거나 공개 프로필), 관계 목록을 판단한다.
 */
public final class SocialCircle {

    private static final Comparator<ExplorerId> BY_ID = Comparator.comparing(ExplorerId::value);

    private final ExplorerId me;
    private final Set<ExplorerId> following;
    private final Set<ExplorerId> followers;

    private SocialCircle(ExplorerId me, Set<ExplorerId> following, Set<ExplorerId> followers) {
        this.me = Objects.requireNonNull(me, "me");
        this.following = following;
        this.followers = followers;
    }

    /** @param outgoing me 가 팔로우하는 관계 · @param incoming me 를 팔로우하는 관계 */
    public static SocialCircle of(ExplorerId me, Collection<Friendship> outgoing, Collection<Friendship> incoming) {
        Set<ExplorerId> following = new TreeSet<>(BY_ID);
        outgoing.stream().filter(friendship -> friendship.from(me)).forEach(friendship -> following.add(friendship.followee()));
        Set<ExplorerId> followers = new TreeSet<>(BY_ID);
        incoming.stream().filter(friendship -> friendship.to(me)).forEach(friendship -> followers.add(friendship.follower()));
        return new SocialCircle(me, following, followers);
    }

    /** 두 탐험가가 서로 팔로우하는지 — between = 둘 사이의 관계(0~2건). */
    public static boolean mutual(ExplorerId one, ExplorerId other, Collection<Friendship> between) {
        return between.stream().anyMatch(friendship -> friendship.from(one) && friendship.to(other))
            && between.stream().anyMatch(friendship -> friendship.from(other) && friendship.to(one));
    }

    public boolean isMutual(ExplorerId other) {
        return following.contains(other) && followers.contains(other);
    }

    /** 친구(맞팔로우), id 순. */
    public List<ExplorerId> mutualFriends() {
        return following.stream().filter(followers::contains).toList();
    }

    public int mutualCount() {
        return mutualFriends().size();
    }

    /** 내가 팔로우하는 사람(친구 소식 대상), id 순. */
    public List<ExplorerId> following() {
        return List.copyOf(following);
    }

    /**
     * 친구 랭킹 대상: 나(항상) + 프로필이 나에게 보이는 친구(리더 결정 1 — PRIVATE 인 맞팔 친구는 숨긴다, FRIENDS 친구는 맞팔이라 보인다).
     *
     * @param visibleToMe 프로필이 나에게 보이는 탐험가(공유의 공개 범위 판정 결과)
     */
    public List<ExplorerId> rankingMembers(Set<ExplorerId> visibleToMe) {
        return Stream.concat(Stream.of(me), mutualFriends().stream().filter(visibleToMe::contains)).toList();
    }

    /**
     * 관계 목록(GET /friends): 팔로잉 ∪ 팔로워, 친구 먼저 → 내가 팔로우 → 나를 팔로우, 같으면 id 순. 내가 한쪽으로만 팔로우하는데 프로필이
     * 나에게 보이지 않는 사람(PRIVATE·친구 아닌 FRIENDS)은 빼다 — 그 handle 이 있다는 사실을 팔로우 응답과 같은 수준으로 숨긴다(QA P3-3).
     * 나를 팔로우하는 사람·친구는 그대로 보인다(스스로 관계를 맺었다).
     */
    public List<FriendRelation> relations(Set<ExplorerId> visibleToMe) {
        Set<ExplorerId> everyone = new LinkedHashSet<>(following);
        everyone.addAll(followers);
        return everyone.stream()
            .map(other -> new FriendRelation(other, following.contains(other), followers.contains(other)))
            .filter(relation -> relation.follower() || visibleToMe.contains(relation.explorerId()))
            .sorted(Comparator.comparing((FriendRelation relation) -> !relation.mutual())
                .thenComparing(relation -> !relation.following())
                .thenComparing(FriendRelation::explorerId, BY_ID))
            .toList();
    }

    /** other 와의 관계(관계가 없으면 팔로우 둘 다 false). */
    public FriendRelation relationTo(ExplorerId other) {
        return new FriendRelation(other, following.contains(other), followers.contains(other));
    }

    /**
     * 영토 비교(VS) 허용 판단: 자기 자신이면 CANNOT_COMPARE_SELF, 대상의 프로필이 나에게 보이면(PUBLIC, 또는 FRIENDS 인데 맞팔 — 공유가
     * 판정) 허용, 아니면 PROFILE_NOT_FOUND(비공개 프로필과 같은 응답 — 존재 숨김). PRIVATE 인 맞팔 친구도 비교하지 않는다(리더 결정 1).
     */
    public void requireComparableWith(ExplorerId target, boolean profileVisibleToMe) {
        if (me.equals(target)) throw SocialError.CANNOT_COMPARE_SELF.exception();
        if (!profileVisibleToMe) throw SocialError.PROFILE_NOT_FOUND.exception();
    }

    /**
     * 나에게 드러난 팔로잉 — 내가 팔로우하는 사람 중 프로필이 나에게 보이거나 나를 팔로우하는 사람(QA r2 P2-A). 숨은 대상에 대한 팔로우는
     * 내가 보는 어떤 수치·목록에도 들어가지 않는다(없는 handle 을 팔로우한 것과 구별되지 않게).
     */
    public List<ExplorerId> revealedFollowing(Set<ExplorerId> visibleToMe) {
        return following.stream().filter(other -> visibleToMe.contains(other) || followers.contains(other)).toList();
    }

    /** 나를 팔로우하는 사람인지(맞팔로우 요청 — 숨은 프로필이라도 나를 팔로우하면 맞팔할 수 있다). */
    public boolean followedBy(ExplorerId other) {
        return followers.contains(other);
    }

    public ExplorerId me() {
        return me;
    }

    @Override
    public String toString() {
        return "SocialCircle[" + me + ", following=" + following.size() + ", followers=" + followers.size() + ", mutual="
            + following.stream().filter(followers::contains).map(ExplorerId::value).collect(Collectors.joining(",")) + "]";
    }
}
