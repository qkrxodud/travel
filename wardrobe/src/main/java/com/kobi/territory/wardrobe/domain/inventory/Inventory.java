package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.wardrobe.domain.item.ItemSpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 보유 아이템 애그리거트(explorerId).
 *
 * 불변식
 * - 같은 itemId 는 한 번만(지급 재전달은 no-op).
 * - 체크인으로 받은 아이템(지역 특산물 REGION, 기간·시·도 이슈 EVENT)은 체크인한 본인에게 주고, 그 근거 방문(같은 조건을
 *   만족한 활성 방문 — 다른 지도 포함)이 모두 취소됐을 때만 회수한다(탐험가 단위, 리더 결정 Q2). 옛 세대 이벤트는 무시한다.
 * - 세트 보상(SET_REWARD)·수동 지급은 회수하지 않는다(취소 비대칭). 탈퇴는 회수 사유가 아니다(방문 "취소"만).
 * - 초대 보상(4단계, EVENT·회수 없음): 공유 지도에 초대받아 처음 합류하면 받는다. 같은 초대자와는 한 번만, 셀프 초대·재가입은
 *   대상 아님({@link Invitations}). 초대한 쪽 보상은 그 사람 Inventory 가 따로 받는다.
 * - 계절 회차 배경(9단계, EVENT·회수 없음): 회차 완성 시점 멤버 각자가 받는다.
 * - 재방문 색 변형(9단계): 도장을 받은 지역의 특산물은 2회차 색 변형으로 그린다({@link RevisitMarks}) — 새 아이템이 아니라 보유 아이템의
 *   변형 속성이고, 도장처럼 지우지 않는다.
 */
public final class Inventory {

    private final ExplorerId explorerId;
    private final OwnedItems ownedItems;
    private final VisitTraces visitTraces;
    private final Invitations invitations;
    private final RevisitMarks revisitMarks;
    private Instant updatedAt;

    private Inventory(ExplorerId explorerId, OwnedItems ownedItems, VisitTraces visitTraces, Invitations invitations,
                      RevisitMarks revisitMarks, Instant updatedAt) {
        this.explorerId = Objects.requireNonNull(explorerId, "explorerId");
        this.ownedItems = Objects.requireNonNull(ownedItems, "ownedItems");
        this.visitTraces = Objects.requireNonNull(visitTraces, "visitTraces");
        this.invitations = Objects.requireNonNull(invitations, "invitations");
        this.revisitMarks = Objects.requireNonNull(revisitMarks, "revisitMarks");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** 아직 아무것도 없는 가방. */
    public static Inventory empty(ExplorerId explorerId, Instant at) {
        return new Inventory(explorerId, OwnedItems.empty(), VisitTraces.empty(), Invitations.empty(), RevisitMarks.empty(), at);
    }

    public static Inventory restore(ExplorerId explorerId, OwnedItems ownedItems, VisitTraces visitTraces, Instant updatedAt) {
        return restore(explorerId, ownedItems, visitTraces, Invitations.empty(), updatedAt);
    }

    public static Inventory restore(ExplorerId explorerId, OwnedItems ownedItems, VisitTraces visitTraces,
                                    Invitations invitations, Instant updatedAt) {
        return restore(explorerId, ownedItems, visitTraces, invitations, RevisitMarks.empty(), updatedAt);
    }

    public static Inventory restore(ExplorerId explorerId, OwnedItems ownedItems, VisitTraces visitTraces,
                                    Invitations invitations, RevisitMarks revisitMarks, Instant updatedAt) {
        return new Inventory(explorerId, ownedItems, visitTraces, invitations, revisitMarks, updatedAt);
    }

    // ---- 커맨드 ----------------------------------------------------------------------------------------------

    /** 본인 체크인(RegionVisited): 방문 흔적 반영 → 이번 체크인의 지급 아이템 중 없는 것을 얻는다. 옛 세대면 no-op. */
    public InventoryChange applyCheckIn(CheckInGrant grant) {
        if (!visitTraces.visit(grant.region(), grant.mapId(), grant.generation())) return InventoryChange.none(grant.at());
        VisitKey visit = new VisitKey(grant.region(), grant.mapId());
        List<OwnedItem> granted = new ArrayList<>();
        grant.items().forEach(item -> ownedItems.grantByVisit(item.itemId(), item.grantKind(), visit, grant.at())
            .ifPresent(granted::add));
        return settle(granted, List.of(), grant.at());
    }

    /** 본인 체크인 취소(VisitCancelled): 그 방문을 근거에서 빼고, 근거가 남지 않은 체크인 아이템을 회수한다. 옛 세대면 no-op. */
    public InventoryChange applyCancel(String mapId, RegionCode region, long generation, Instant at) {
        List<OwnedItem> revoked = visitTraces.cancel(region, mapId, generation)
            ? ownedItems.withdraw(new VisitKey(region, mapId)) : List.of();
        return settle(List.of(), revoked, at);
    }

    /** 보상 지급(테마 완성 SetCompleted, 지도 합류 MemberJoined 의 이미 완성된 테마). 회수 없음. */
    public InventoryChange grantRewards(List<ItemSpec> items, Instant at) {
        List<OwnedItem> granted = new ArrayList<>();
        items.forEach(item -> ownedItems.grantReward(item.itemId(), item.grantKind(), at).ifPresent(granted::add));
        return settle(granted, List.of(), at);
    }

    /**
     * 초대받아 공유 지도에 합류(MemberJoined.invitedBy): 보상 대상(초대자 있음·재가입 아님·셀프 아님·이 초대자와 처음)이면 기록하고
     * 초대받은 쪽 아이템(guestItems — 카탈로그 INVITATION/GUEST)을 받는다. 대상이 아니면 no-op(재전달도 no-op).
     * @return 받은 아이템 + 보상해야 할 초대자(초대자 Inventory 는 따로 — InviteRewardOwed)
     */
    public InvitationOutcome acceptInvitation(Invitation invitation, List<ItemSpec> guestItems) {
        return invitations.accept(explorerId, invitation)
            .map(record -> new InvitationOutcome(grantRewards(guestItems, invitation.joinedAt()), record.inviterId()))
            .orElseGet(() -> new InvitationOutcome(InventoryChange.none(invitation.joinedAt()), null));
    }

    /**
     * 계정 병합(ExplorerMerged — 익명 탐험가 merged → 이 계정 탐험가): 재계산으로 다시 만들 수 없는 아이템(수동·초대 보상, EVENT)과
     * 초대 기록을 옮긴다(4단계 QA P3-6). 같은 아이템은 중복 없음, 재전달은 no-op. 방문형·테마 아이템은 재계산이 만든다.
     */
    public InventoryChange absorbMerged(Inventory merged, Instant at) {
        List<OwnedItem> adopted = ownedItems.adoptUnreplayable(merged.ownedItems);
        invitations.adopt(explorerId, merged.invitations);
        revisitMarks.adopt(merged.revisitMarks.all());
        return settle(adopted, List.of(), at);
    }

    /**
     * 재계산(InventoryReplay) 출발점: 다시 재생할 지도(지금 멤버인 지도)의 방문 흔적과 그 근거만 비운다. 재생할 수 없는 지도
     * (탈퇴·purge 로 방문이 숨겨지거나 지워진 지도)의 흔적·근거와 보상 아이템은 그대로 둔다 — 이벤트 누적에서도 탈퇴는 회수하지 않으므로.
     */
    Inventory rebuildBase(Set<String> replayableMaps, Instant at) {
        return new Inventory(explorerId, ownedItems.rebuildBase(replayableMaps), visitTraces.rebuildBase(replayableMaps),
            invitations, RevisitMarks.of(revisitMarks.all()), at);
    }

    /**
     * 재방문 도장(RevisitStamped, 9단계): 그 지역 특산물을 2회차 색 변형으로 — 아이템을 지금 갖고 있지 않아도 표시는 남긴다(나중에 다시 얻으면
     * 변형으로 보인다). 멱등. @return 새로 표시했는지
     */
    public boolean markRevisited(RegionCode region, Instant at) {
        boolean marked = revisitMarks.mark(region, at);
        if (marked) updatedAt = at;
        return marked;
    }

    /** 재계산: 탐험이 기록한 도장 지역을 모두 표시한다(빠진 것만 — 지우지 않는다). */
    void adoptRevisits(List<RevisitMark> marks) {
        revisitMarks.adopt(marks);
    }

    /** 아이템의 색 변형 번호(지역 특산물의 지역을 넘긴다, 지역 아이템이 아니면 null → 1). */
    public int variantOf(RegionCode itemRegion) {
        return revisitMarks.variantOf(itemRegion);
    }

    /** 재계산 마무리: 예전에도 있던 아이템은 처음 얻은 시각·즐겨찾기를 유지한다. */
    void adoptHistory(Inventory previous) {
        ownedItems.adoptHistory(previous.ownedItems);
    }

    /** 즐겨찾기 표시(가방에 있는 아이템만). */
    public OwnedItem markFavorite(String itemId, boolean favorite, Instant at) {
        OwnedItem item = ownedItems.markFavorite(itemId, favorite);
        updatedAt = at;
        return item;
    }

    private InventoryChange settle(List<OwnedItem> granted, List<OwnedItem> revoked, Instant at) {
        updatedAt = at;
        return new InventoryChange(granted, revoked, at);
    }

    // ---- 조회 ----------------------------------------------------------------------------------------------

    public boolean owns(String itemId) {
        return ownedItems.owns(itemId);
    }

    public Optional<OwnedItem> find(String itemId) {
        return ownedItems.find(itemId);
    }

    public ExplorerId explorerId() { return explorerId; }
    public OwnedItems ownedItems() { return ownedItems; }
    public VisitTraces visitTraces() { return visitTraces; }
    public Invitations invitations() { return invitations; }
    public RevisitMarks revisitMarks() { return revisitMarks; }
    public Instant updatedAt() { return updatedAt; }
}
