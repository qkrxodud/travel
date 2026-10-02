package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.model.ExplorerId;
import java.util.List;
import java.util.Optional;

/**
 * 일급 컬렉션: 지도 멤버. 불변식 — 멤버 ≤ {@link #MAX}, OWNER 정확히 1명, 같은 탐험가 중복 불가.
 * 멤버십 판정(requireMember)도 여기서 한다.
 */
public final class Members {

    public static final int MAX = 4;

    private final List<Member> items;

    private Members(List<Member> items) {
        if (items.size() > MAX) throw ExplorationError.MAP_FULL.exception(MAX);
        long owners = items.stream().filter(member -> member.role() == MemberRole.OWNER).count();
        if (owners != 1) throw ExplorationError.INVALID_MAP.exception("OWNER 수=" + owners);
        if (items.stream().map(Member::explorerId).distinct().count() != items.size()) {
            throw ExplorationError.ALREADY_MEMBER.exception();
        }
        this.items = List.copyOf(items);
    }

    public static Members of(List<Member> members) {
        return new Members(members);
    }

    public static Members ownerOnly(Member owner) {
        return new Members(List.of(owner));
    }

    public Optional<Member> find(ExplorerId explorerId) {
        return items.stream().filter(member -> member.explorerId().equals(explorerId)).findFirst();
    }

    /** "이 탐험가가 이 지도의 멤버인가" — 아니면 NOT_A_MEMBER. */
    public Member require(ExplorerId explorerId) {
        return find(explorerId).orElseThrow(ExplorationError.NOT_A_MEMBER::exception);
    }

    public Member owner() {
        return items.stream().filter(member -> member.role() == MemberRole.OWNER).findFirst().orElseThrow();
    }

    public int size() {
        return items.size();
    }

    public List<Member> asList() {
        return items;
    }
}
