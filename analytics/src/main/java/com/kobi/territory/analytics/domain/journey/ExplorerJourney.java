package com.kobi.territory.analytics.domain.journey;

import com.kobi.territory.analytics.domain.actor.ExplorerHash;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * 탐험가 한 명의 여정 — 코호트·퍼널·K 계수의 기준이 되는 날짜들. 모두 <b>처음 한 번만</b> 정해진다(사실이 다시 와도 바뀌지 않는다).
 * <ul>
 *   <li>가입한 날(createdDay) — 리텐션 코호트. 분석을 켜기 전에 가입한 탐험가는 모른다(null) — 코호트·첫 체크인 퍼널에 넣지 않는다</li>
 *   <li>첫 체크인 날과 재방문 마감일 — 첫 체크인 다음 날부터 마감일까지 다시 오면 퍼널 3단계</li>
 *   <li>초대로 처음 합류한 날 — 가입 후 정해진 일수 안이면 초대 유입(K 계수)</li>
 * </ul>
 */
public final class ExplorerJourney {

    private final ExplorerHash explorerHash;
    private LocalDate createdDay;
    private LocalDate firstCheckInDay;
    private LocalDate revisitDeadline;
    private LocalDate invitedJoinDay;
    private boolean inviteAcquired;

    private ExplorerJourney(ExplorerHash explorerHash, LocalDate createdDay, LocalDate firstCheckInDay, LocalDate revisitDeadline,
                            LocalDate invitedJoinDay, boolean inviteAcquired) {
        this.explorerHash = Objects.requireNonNull(explorerHash, "explorerHash");
        this.createdDay = createdDay;
        this.firstCheckInDay = firstCheckInDay;
        this.revisitDeadline = revisitDeadline;
        this.invitedJoinDay = invitedJoinDay;
        this.inviteAcquired = inviteAcquired;
    }

    /** 가입 사실로 시작하는 여정. */
    public static ExplorerJourney begin(ExplorerHash explorerHash, LocalDate createdDay) {
        return new ExplorerJourney(explorerHash, Objects.requireNonNull(createdDay, "createdDay"), null, null, null, false);
    }

    /** 가입 사실 없이 처음 본 탐험가(분석을 켜기 전에 가입) — 가입일을 모른다. */
    public static ExplorerJourney unknownStart(ExplorerHash explorerHash) {
        return new ExplorerJourney(explorerHash, null, null, null, null, false);
    }

    public static ExplorerJourney restore(ExplorerHash explorerHash, LocalDate createdDay, LocalDate firstCheckInDay,
                                          LocalDate revisitDeadline, LocalDate invitedJoinDay, boolean inviteAcquired) {
        return new ExplorerJourney(explorerHash, createdDay, firstCheckInDay, revisitDeadline, invitedJoinDay, inviteAcquired);
    }

    /** 가입 사실이 늦게 왔으면 가입일을 채운다(이미 있으면 그대로). @return 바뀌었는지 */
    public boolean created(LocalDate day) {
        if (createdDay != null) return false;
        createdDay = day;
        return true;
    }

    /**
     * 체크인 사실. 가입일을 아는 탐험가의 처음 체크인이면 첫 체크인으로 적고 재방문 마감일을 정한다.
     * @return 이번이 첫 체크인인지
     */
    public boolean checkedIn(LocalDate day, JourneyPolicy policy) {
        if (createdDay == null || firstCheckInDay != null) return false;
        firstCheckInDay = day;
        revisitDeadline = day.plusDays(policy.revisitWindowDays());
        return true;
    }

    /**
     * 초대(초대코드·프로필 링크)로 공유 지도에 합류한 사실. 처음 합류만 적고, 가입 후 정해진 일수 안이면 초대 유입이다.
     * @return 바뀌었는지
     */
    public boolean joinedByInvite(LocalDate day, JourneyPolicy policy) {
        if (invitedJoinDay != null) return false;
        invitedJoinDay = day;
        inviteAcquired = createdDay != null && !day.isAfter(createdDay.plusDays(policy.inviteAttributionDays()));
        return true;
    }

    public ExplorerHash explorerHash() {
        return explorerHash;
    }

    public Optional<LocalDate> createdDay() {
        return Optional.ofNullable(createdDay);
    }

    public Optional<LocalDate> firstCheckInDay() {
        return Optional.ofNullable(firstCheckInDay);
    }

    public Optional<LocalDate> revisitDeadline() {
        return Optional.ofNullable(revisitDeadline);
    }

    public Optional<LocalDate> invitedJoinDay() {
        return Optional.ofNullable(invitedJoinDay);
    }

    public boolean inviteAcquired() {
        return inviteAcquired;
    }
}
