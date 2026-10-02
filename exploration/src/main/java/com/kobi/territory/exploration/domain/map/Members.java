package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.common.model.ExplorerId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 일급 컬렉션: 지도의 현재 멤버(탈퇴 유예 중인 사람 제외). 불변식 — 멤버 ≤ {@link #MAX}, OWNER 정확히 1명,
 * 같은 탐험가 중복 불가. 멤버십 판정(requireMember)도 여기서 한다. 불변 — 변경은 새 Members 를 돌려준다.
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

    /** 멤버 추가(이미 멤버면 ALREADY_MEMBER, 가득 차면 MAP_FULL). */
    Members with(Member member) {
        if (find(member.explorerId()).isPresent()) throw ExplorationError.ALREADY_MEMBER.exception();
        if (items.size() >= MAX) throw ExplorationError.MAP_FULL.exception(MAX);
        List<Member> next = new ArrayList<>(items);
        next.add(member);
        return new Members(next);
    }

    Members without(ExplorerId explorerId) {
        Member leaving = require(explorerId);
        List<Member> next = new ArrayList<>(items);
        next.remove(leaving);
        return new Members(next);
    }

    /** 지도장 넘기기: 현재 OWNER 는 MEMBER, 대상은 OWNER. */
    Members transferOwnerTo(ExplorerId newOwner) {
        require(newOwner);
        return new Members(items.stream().map(member -> member.explorerId().equals(newOwner)
            ? member.withRole(MemberRole.OWNER)
            : member.withRole(MemberRole.MEMBER)).toList());
    }

    public int size() {
        return items.size();
    }

    /** 가입 순(같으면 id 순) — 멤버별 색 배정 순서. */
    public List<Member> byJoinOrder() {
        return items.stream().sorted(Comparator.comparing(Member::joinedAt)
            .thenComparing(member -> member.explorerId().value())).toList();
    }

    public List<Member> asList() {
        return items;
    }
}
