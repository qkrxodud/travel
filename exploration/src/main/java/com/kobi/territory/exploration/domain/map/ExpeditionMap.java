package com.kobi.territory.exploration.domain.map;

import com.kobi.territory.exploration.domain.policy.CheckInPolicy;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.common.model.ExplorerId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.Objects;
import java.util.Optional;

/**
 * 지도 애그리거트(공유 지도의 단위, Territory의 키 주인).
 * 가입 시 개인 지도(PERSONAL, 멤버 1명)가 자동으로 생기고, 공유 지도(SHARED)는 만든 사람이 지도장(OWNER)이 되어
 * 초대코드로 친구를 모은다(§2-9). 방문(Territory)은 같은 트랜잭션에서 고치지 않는다 — 탈퇴·재가입의 방문 숨김·복구는
 * 이벤트(MemberLeft·MemberJoined)로 Territory 가 따로 처리한다.
 *
 * 불변식
 * - 멤버 규칙(≤4, OWNER 1명, 중복 불가)은 일급 컬렉션 {@link Members}. owner 일치, PERSONAL 은 멤버 1명.
 * - 지도장은 탈퇴할 수 없다(양도 후 가능). 개인 지도는 합류·탈퇴·양도할 수 없다.
 * - 지도장만: 설정 변경, 초대코드 재발급, 지도장 넘기기, 방문 이의.
 * - 탈퇴는 유예(Departure): 유예 안에 돌아오면 원래 가입 시각으로 복귀(rejoined), 유예가 끝나면 기록을 지운다.
 */
public final class ExpeditionMap {

    public static final int MAX_MEMBERS = Members.MAX;
    public static final int MAX_NAME_LENGTH = 40;

    private final MapId id;
    private final String name;
    private final CountryCode country;
    private final MapKind kind;
    private final Instant createdAt;
    private InviteCode inviteCode;
    private ExplorerId ownerId;
    private MapSettings settings;
    private Members members;
    private final Departures departures;

    private ExpeditionMap(MapId id, String name, CountryCode country, InviteCode inviteCode, ExplorerId ownerId,
                          MapKind kind, MapSettings settings, Instant createdAt, List<Member> members,
                          List<Departure> departures) {
        this.id = Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank() || name.strip().length() > MAX_NAME_LENGTH) {
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
        this.departures = Departures.of(departures);
        if (!this.members.owner().explorerId().equals(ownerId)) throw ExplorationError.INVALID_MAP.exception("owner 불일치");
        if (kind == MapKind.PERSONAL && (this.members.size() != 1 || this.departures.size() != 0)) {
            throw ExplorationError.INVALID_MAP.exception("개인 지도는 멤버 1명");
        }
    }

    /** 지도 생성. 생성자가 OWNER 멤버 1명으로 들어간다. */
    public static ExpeditionMap create(MapId id, ExplorerId owner, String name, CountryCode country, InviteCode inviteCode,
                                       MapKind kind, MapSettings settings, Instant now) {
        return new ExpeditionMap(id, name, country, inviteCode, owner, kind, settings, now,
            List.of(new Member(owner, MemberRole.OWNER, now)), List.of());
    }

    public static ExpeditionMap restore(MapId id, String name, CountryCode country, InviteCode inviteCode, ExplorerId ownerId,
                                        MapKind kind, MapSettings settings, Instant createdAt, List<Member> members) {
        return restore(id, name, country, inviteCode, ownerId, kind, settings, createdAt, members, List.of());
    }

    public static ExpeditionMap restore(MapId id, String name, CountryCode country, InviteCode inviteCode, ExplorerId ownerId,
                                        MapKind kind, MapSettings settings, Instant createdAt, List<Member> members,
                                        List<Departure> departures) {
        return new ExpeditionMap(id, name, country, inviteCode, ownerId, kind, settings, createdAt, members, departures);
    }

    // ---- 커맨드 ----------------------------------------------------------------------------------------------

    /**
     * 초대코드로 합류. 유예 안의 탈퇴 기록이 있으면 재가입(원래 가입 시각, 방문 복구 대상), 유예가 끝난 기록이 남아 있으면
     * 지우고(숨긴 방문 삭제 대상) 새 멤버로 들어온다.
     */
    public JoinResult join(ExplorerId explorerId, Instant now, Duration leaveGrace) {
        return admit(explorerId, ownerId, now, leaveGrace);
    }

    /**
     * 공개 프로필 링크로 합류(4단계). 프로필 주인의 공개 프로필이 열려 있고(profileOpen — 공유의 공개 범위, QA P3-5), 그가
     * 지도장이고 공개 범위가 PUBLIC 인 공유 지도만 열려 있다 — 아니면 PROFILE_MAP_NOT_FOUND(어느 조건이 틀렸는지 숨긴다).
     * 초대한 사람 = 프로필 주인.
     */
    public JoinResult joinViaProfile(ExplorerId profileOwner, boolean profileOpen, ExplorerId explorerId, Instant now,
                                     Duration leaveGrace) {
        if (!profileOpen || !openToProfileOf(profileOwner)) throw ExplorationError.PROFILE_MAP_NOT_FOUND.exception();
        return admit(explorerId, profileOwner, now, leaveGrace);
    }

    private JoinResult admit(ExplorerId explorerId, ExplorerId inviter, Instant now, Duration leaveGrace) {
        requireShared("합류");
        Optional<Departure> departure = departures.find(explorerId);
        Departure expired = departure.filter(previous -> previous.expired(now, leaveGrace)).orElse(null);
        boolean rejoined = departure.isPresent() && expired == null;
        Member member = new Member(explorerId, MemberRole.MEMBER, rejoined ? departure.get().joinedAt() : now);
        members = members.with(member);
        departure.ifPresent(departures::remove);
        return new JoinResult(member, rejoined, expired, inviter);
    }

    /** 탈퇴(유예 시작). 지도장은 넘긴 뒤에만. */
    public Departure leave(ExplorerId explorerId, Instant now) {
        requireShared("탈퇴");
        Member leaving = members.require(explorerId);
        if (leaving.owner()) throw ExplorationError.OWNER_CANNOT_LEAVE.exception();
        members = members.without(explorerId);
        Departure departure = new Departure(explorerId, leaving.joinedAt(), now);
        departures.add(departure);
        return departure;
    }

    /**
     * 병합(claimExplorer): 익명 탐험가 from 이 계정 탐험가 into 로 병합될 때 이 지도의 from 자리를 into 에게 넘긴다(사용자 결정 Q2 —
     * from 의 방문·선점은 탈퇴가 아니라 재귀속으로 into 에게 간다, Territory.reassignMember). 그래서 from 의 탈퇴 유예 기록은 만들지 않는다.
     * <ul>
     *   <li>into 가 이미 멤버 → from 만 멤버에서 빠진다. from 이 지도장이었으면 into 가 지도장.</li>
     *   <li>into 가 멤버가 아님 → into 가 from 의 자리(역할·가입 시각 — 온보딩 예외 재사용 방지)를 잇는다. into 가 유예 중 탈퇴 기록이
     *       있으면 재가입(원래 가입 시각, 숨긴 방문 복구 대상), 유예가 끝난 기록이면 지운다(숨긴 방문 삭제 대상). 자리는 from 이 비운 자리라 늘 있다.</li>
     * </ul>
     * from 이 이미 멤버가 아니면 빈 값(멱등 — 재전달 안전).
     */
    public Optional<Handover> handOver(ExplorerId from, ExplorerId into, Instant now, Duration leaveGrace) {
        requireShared("병합");
        Optional<Member> found = members.find(from);
        if (found.isEmpty()) return Optional.empty();
        Member leaving = found.get();
        if (members.find(into).isPresent()) {
            if (leaving.owner()) transferOwnershipTo(into);
            members = members.without(from);
            return Optional.of(new Handover(null, false, null));
        }
        Optional<Departure> previous = departures.find(into);
        Departure expired = previous.filter(earlier -> earlier.expired(now, leaveGrace)).orElse(null);
        boolean rejoined = previous.isPresent() && expired == null;
        Member joined = new Member(into, leaving.role(), rejoined ? previous.get().joinedAt() : leaving.joinedAt());
        members = members.replace(from, joined);
        if (leaving.owner()) ownerId = into;
        previous.ifPresent(departures::remove);
        return Optional.of(new Handover(joined, rejoined, expired));
    }

    /** 지도장 넘기기(지도장만, 대상은 현재 멤버). */
    public void transferOwner(ExplorerId requester, ExplorerId newOwner) {
        requireShared("지도장 넘기기");
        requireOwner(requester);
        if (requester.equals(newOwner)) return;
        transferOwnershipTo(newOwner);
    }

    private void transferOwnershipTo(ExplorerId newOwner) {
        members = members.transferOwnerTo(newOwner);
        ownerId = newOwner;
    }

    /** 초대코드 재발급(지도장만). 새 코드의 전체 유일성은 호출자(저장소 확인 + DB UNIQUE)가 지킨다. */
    public void regenerateInviteCode(ExplorerId requester, InviteCode newCode) {
        requireOwner(requester);
        inviteCode = Objects.requireNonNull(newCode, "newCode");
    }

    /**
     * 설정 변경(공유 지도의 지도장만). 하루 상한은 낮추기만 할 수 있다 — 1..maxDailyCap(= territory.check-in.daily-cap,
     * 치팅 대응 §7). 개인 지도는 설정을 바꿀 수 없다(상한은 늘 설정 기본값).
     */
    public void changeSettings(ExplorerId requester, MapSettings next, int maxDailyCap) {
        requireShared("설정 변경");
        requireOwner(requester);
        Objects.requireNonNull(next, "settings");
        if (next.dailyCheckInCap() > maxDailyCap) {
            throw ExplorationError.INVALID_SETTINGS.exception("dailyCheckInCap=" + next.dailyCheckInCap() + " (1~" + maxDailyCap + ")");
        }
        settings = next;
    }

    /** 유예가 끝난 탈퇴 기록을 지운다(배치). @return 지운 기록 — 그 멤버의 숨긴 방문을 하드 삭제할 대상 */
    public List<Departure> purgeExpired(Instant now, Duration leaveGrace) {
        return departures.removeExpired(now, leaveGrace);
    }

    /** 지도장 권한 확인 — 아니면 OWNER_ONLY(멤버가 아니면 NOT_A_MEMBER). */
    public Member requireOwner(ExplorerId requester) {
        Member member = members.require(requester);
        if (!member.owner()) throw ExplorationError.OWNER_ONLY.exception();
        return member;
    }

    // ---- 조회 ----------------------------------------------------------------------------------------------

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

    public boolean shared() {
        return kind == MapKind.SHARED;
    }

    /**
     * 이 지도의 하루 체크인 상한을 함께 쓰는 재방문 도장 수(9단계) — 도장은 탐험가 단위 기록이라 개인 지도의 상한을 함께 쓴다(공유 지도는 0).
     *
     * @param stampsToday 그 멤버가 오늘 받은 재방문 도장 수 — 개인 지도일 때만 센다(공유 지도 체크인은 도장첩을 읽지 않는다, QA P3-10)
     */
    public int capSharedWithStamps(IntSupplier stampsToday) {
        return shared() ? 0 : stampsToday.getAsInt();
    }

    /** 이 탐험가의 공개 프로필에서 합류할 수 있는 지도인지 — 그가 지도장인 공유 지도이고 공개 범위가 PUBLIC(4단계). */
    public boolean openToProfileOf(ExplorerId profileOwner) {
        return shared() && ownerId.equals(profileOwner) && settings.visibility().openToProfile();
    }

    public int memberCount() {
        return members.size();
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
    /** 가입 순 멤버(색 배정 순서). */
    public List<Member> membersByJoinOrder() { return members.byJoinOrder(); }
    public List<ExplorerId> memberIds() { return members.byJoinOrder().stream().map(Member::explorerId).toList(); }
    public List<Departure> departures() { return departures.asList(); }

    private void requireShared(String action) {
        if (kind != MapKind.SHARED) throw ExplorationError.PERSONAL_MAP_ONLY_ME.exception(action);
    }
}
