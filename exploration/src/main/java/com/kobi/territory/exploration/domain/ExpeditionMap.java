package com.kobi.territory.exploration.domain;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 지도 애그리거트(공유 지도의 단위, Territory의 키 주인).
 * 1단계는 개인 지도 자동 생성에 필요한 최소(create·멤버 조회)만 구현한다. join/leave/transferOwner/
 * regenerateInviteCode 와 탈퇴 유예는 3단계.
 *
 * 불변식: 멤버 규칙(≤4, OWNER 1명, 중복 불가)은 일급 컬렉션 {@link Members}, owner 일치·PERSONAL은 1명은 여기서.
 */
public final class ExpeditionMap {

    public static final int MAX_MEMBERS = Members.MAX;
    public static final int MAX_NAME_LENGTH = 40;

    private final MapId id;
    private final String name;
    private final CountryCode country;
    private final InviteCode inviteCode;
    private final ExplorerId ownerId;
    private final MapKind kind;
    private final MapSettings settings;
    private final Instant createdAt;
    private final Members members;

    private ExpeditionMap(MapId id, String name, CountryCode country, InviteCode inviteCode, ExplorerId ownerId,
                          MapKind kind, MapSettings settings, Instant createdAt, List<Member> members) {
        this.id = Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw ExplorationError.INVALID_MAP.exception("name=" + name);
        }
        this.name = name.strip();
        this.country = Objects.requireNonNull(country, "country");
        this.inviteCode = Objects.requireNonNull(inviteCode, "inviteCode");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.members = Members.of(members);
        if (!this.members.owner().explorerId().equals(ownerId)) throw ExplorationError.INVALID_MAP.exception("owner 불일치");
        if (kind == MapKind.PERSONAL && this.members.size() != 1) {
            throw ExplorationError.INVALID_MAP.exception("개인 지도는 멤버 1명");
        }
    }

    /** 지도 생성. 생성자가 OWNER 멤버 1명으로 들어간다. */
    public static ExpeditionMap create(MapId id, ExplorerId owner, String name, CountryCode country, InviteCode inviteCode,
                                       MapKind kind, MapSettings settings, Instant now) {
        return new ExpeditionMap(id, name, country, inviteCode, owner, kind, settings, now,
            List.of(new Member(owner, MemberRole.OWNER, now)));
    }

    public static ExpeditionMap restore(MapId id, String name, CountryCode country, InviteCode inviteCode, ExplorerId ownerId,
                                        MapKind kind, MapSettings settings, Instant createdAt, List<Member> members) {
        return new ExpeditionMap(id, name, country, inviteCode, ownerId, kind, settings, createdAt, members);
    }

    public Optional<Member> member(ExplorerId explorerId) {
        return members.find(explorerId);
    }

    /** 체크인 전 "이 탐험가가 이 지도의 멤버인가" 확인(읽기 참조). */
    public Member requireMember(ExplorerId explorerId) {
        return members.require(explorerId);
    }

    /** 이 지도에서 해당 멤버에게 적용할 체크인 정책. */
    public CheckInPolicy checkInPolicy(Duration onboardingGrace) {
        return new CheckInPolicy(settings.dailyCheckInCap(), onboardingGrace, settings.photoRequired());
    }

    public MapId id() { return id; }
    public String name() { return name; }
    public CountryCode country() { return country; }
    public InviteCode inviteCode() { return inviteCode; }
    public ExplorerId ownerId() { return ownerId; }
    public MapKind kind() { return kind; }
    public MapSettings settings() { return settings; }
    public Instant createdAt() { return createdAt; }
    public List<Member> members() { return members.asList(); }
}
