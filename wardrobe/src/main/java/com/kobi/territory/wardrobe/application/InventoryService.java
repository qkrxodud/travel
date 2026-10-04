package com.kobi.territory.wardrobe.application;

import com.kobi.territory.common.event.EventOutbox;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import com.kobi.territory.exploration.api.event.ExplorerMerged;
import com.kobi.territory.exploration.api.event.MapCreated;
import com.kobi.territory.exploration.api.event.MemberJoined;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.RevisitStamped;
import com.kobi.territory.progression.api.event.SeasonCompleted;
import com.kobi.territory.exploration.api.event.VisitCancelled;
import com.kobi.territory.exploration.api.query.ExplorerProfileQuery;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.progression.api.event.ProvinceConquered;
import com.kobi.territory.progression.api.event.SetCompleted;
import com.kobi.territory.progression.api.event.StreakMilestoneReached;
import com.kobi.territory.wardrobe.domain.item.ItemSpec;
import com.kobi.territory.progression.api.query.CollectionBookQuery;
import com.kobi.territory.wardrobe.api.event.ItemGranted;
import com.kobi.territory.wardrobe.api.event.InviteRewardOwed;
import com.kobi.territory.wardrobe.api.event.ItemRevoked;
import com.kobi.territory.wardrobe.domain.inventory.Invitation;
import com.kobi.territory.wardrobe.domain.inventory.InvitationOutcome;
import com.kobi.territory.wardrobe.domain.inventory.CheckInGrant;
import com.kobi.territory.wardrobe.domain.inventory.Inventory;
import com.kobi.territory.wardrobe.domain.inventory.InventoryChange;
import com.kobi.territory.wardrobe.domain.inventory.InventoryRepository;
import com.kobi.territory.wardrobe.domain.inventory.OwnedItem;
import com.kobi.territory.wardrobe.api.event.ThemeRewardOwed;
import com.kobi.territory.wardrobe.domain.inventory.ThemeRewardRecipients;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 보유 아이템(Inventory) 유스케이스 — "불러와서 → 도메인에 시키고 → 저장 → outbox". 지급 대상 판정은 카탈로그(지급 규칙),
 * 중복·회수(탐험가 단위·세대 번호) 판단은 Inventory·OwnedItems·VisitTraces 가 한다.
 * 이벤트 처리는 릴레이가 구독자 트랜잭션(REQUIRES_NEW) 안에서 부른다.
 */
@Service
public class InventoryService {

    static final String AGGREGATE = "Inventory";
    /** 카탈로그 INVITATION 규칙의 대상(초대한 쪽·초대받은 쪽). */
    static final String HOST = "HOST";
    static final String GUEST = "GUEST";

    private final InventoryRepository inventories;
    private final WardrobeCatalog catalog;
    private final CollectionBookQuery collectionBooks;
    private final TerritoryQuery territories;
    private final ExplorerProfileQuery profiles;
    private final EventOutbox outbox;
    private final Clock clock;
    private final TransactionTemplate writeTx;

    public InventoryService(InventoryRepository inventories, WardrobeCatalog catalog, CollectionBookQuery collectionBooks,
                            TerritoryQuery territories, ExplorerProfileQuery profiles, EventOutbox outbox, Clock clock,
                            PlatformTransactionManager transactionManager) {
        this.profiles = profiles;
        this.inventories = inventories;
        this.catalog = catalog;
        this.collectionBooks = collectionBooks;
        this.territories = territories;
        this.outbox = outbox;
        this.clock = clock;
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    /** 본인 체크인 → 지역 특산물 + 기간·시·도 이슈 아이템. */
    @Transactional
    public void onRegionVisited(RegionVisited event) {
        Inventory inventory = loadLocked(ExplorerId.of(event.explorerId()));
        InventoryChange change = inventory.applyCheckIn(new CheckInGrant(event.mapId(), RegionCode.of(event.regionCode()),
            event.visitGeneration(), catalog.grantedByCheckIn(event.regionCode(), event.provinceCode(), event.visitedAt()),
            event.visitedAt()));
        inventories.save(inventory);
        publish(inventory, change);
    }

    /** 본인 체크인 취소 → 그 지역 방문이 어느 지도에도 남지 않으면 지역 아이템 회수. */
    @Transactional
    public void onVisitCancelled(VisitCancelled event) {
        Inventory inventory = loadLocked(ExplorerId.of(event.explorerId()));
        InventoryChange change = inventory.applyCancel(event.mapId(), RegionCode.of(event.regionCode()),
            event.visitGeneration(), event.cancelledAt());
        inventories.save(inventory);
        publish(inventory, change);
    }

    /**
     * 테마(세트) 완성 — 수령자(완성 시점 지도 멤버)마다 한 건씩 온다(explorerId = 수령자). 그 수령자 Inventory 하나만 고친다
     * (세트 배경 지급, 회수 없음, 기간은 완성 시각 기준). 완성 직후 합류해 수령자 목록에 없는 지금 멤버에게는 ThemeRewardOwed 를
     * 내 각자 트랜잭션에서 주게 한다(QA P3-1·P3-R2-5 — 누가 대상인지는 ThemeRewardRecipients 가 판단). 최종 안전망은 재계산.
     */
    @Transactional
    public void onSetCompleted(SetCompleted event) {
        ExplorerId recipient = ExplorerId.of(event.explorerId());
        grantThemeReward(recipient, event.setId(), event.completedAt());
        ThemeRewardRecipients.lateJoiners(recipient, explorerIdOrNull(event.completedBy()), explorerIdsOrNull(event.recipientIds()),
                territories.memberIdsOf(event.mapId()).stream().map(ExplorerId::of).toList())
            .forEach(member -> outbox.append(AGGREGATE, member.value(),
                new ThemeRewardOwed(event.mapId(), event.setId(), member.value(), event.completedAt())));
    }

    /** 완성 직후 합류한 멤버의 세트 보상(ThemeRewardOwed) — 그 멤버 Inventory 하나만. 멱등. */
    @Transactional
    public void onThemeRewardOwed(ThemeRewardOwed event) {
        grantThemeReward(ExplorerId.of(event.explorerId()), event.setId(), event.completedAt());
    }

    private void grantThemeReward(ExplorerId explorerId, String setId, Instant completedAt) {
        Inventory inventory = loadLocked(explorerId);
        InventoryChange change = inventory.grantRewards(catalog.grantedByThemeCompletion(setId, completedAt), completedAt);
        inventories.save(inventory);
        publish(inventory, change);
    }

    private static ExplorerId explorerIdOrNull(String explorerId) {
        return explorerId == null ? null : ExplorerId.of(explorerId);
    }

    private static List<ExplorerId> explorerIdsOrNull(List<String> explorerIds) {
        return explorerIds == null ? null : explorerIds.stream().map(ExplorerId::of).toList();
    }

    /** 시·도 정복(8단계) → 그 시·도 대표 장식(PROVINCE_COMPLETE). 회수 없음, 아이템 단위로 멱등. */
    @Transactional
    public void onProvinceConquered(ProvinceConquered event) {
        grantAchievement(ExplorerId.of(event.explorerId()),
            catalog.grantedByProvinceConquest(event.provinceCode(), event.conqueredAt()), event.conqueredAt());
    }

    /** 연속 탐험 마일스톤(8단계) → 한정 아이템(STREAK_MILESTONE). 회수 없음, 아이템 단위로 멱등. */
    @Transactional
    public void onStreakMilestoneReached(StreakMilestoneReached event) {
        grantAchievement(ExplorerId.of(event.explorerId()),
            catalog.grantedByStreakMilestone(event.months(), event.reachedAt()), event.reachedAt());
    }

    private void grantAchievement(ExplorerId explorerId, List<ItemSpec> rewards, Instant at) {
        Inventory inventory = loadLocked(recipientOf(explorerId.value()));
        InventoryChange change = inventory.grantRewards(rewards, at);
        inventories.save(inventory);
        publish(inventory, change);
    }

    /** 계절 한정 테마 회차 완성(9단계, 수령자마다) → 회차 배경(SEASON_COMPLETE). 회수 없음, 아이템 단위로 멱등. */
    @Transactional
    public void onSeasonCompleted(SeasonCompleted event) {
        grantAchievement(ExplorerId.of(event.explorerId()),
            catalog.grantedBySeasonCompletion(event.roundId(), event.completedAt()), event.completedAt());
    }

    /**
     * 재방문 도장(9단계) → 그 지역 특산물을 2회차 색 변형으로(보유 아이템의 변형 속성 — 새 아이템·이벤트 없음, 가방·장면 조회가 variant 로 그린다).
     * 멱등.
     */
    @Transactional
    public void onRevisitStamped(RevisitStamped event) {
        Inventory inventory = loadLocked(recipientOf(event.explorerId()));
        inventory.markRevisited(RegionCode.of(event.regionCode()), event.stampedAt());
        inventories.save(inventory);
    }

    /** 지도 생성 — 지도장의 인벤토리 루트 행을 보장한다(가입 때 개인 지도로 미리 생겨, 이후 이벤트 처리·재계산이 항상 있는 행을 잠근다 — S3-1 과 같은 이유). 멱등. */
    @Transactional
    public void onMapCreated(MapCreated event) {
        Inventory inventory = loadLocked(ExplorerId.of(event.ownerId()));
        inventories.save(inventory);
    }

    /**
     * 지도 합류(재가입 포함) — 그 지도에서 이미 완성된 테마의 보상(세트 배경)만 새 멤버에게(XP·칭호 없음, 결정 1).
     * 4단계: 초대받아 처음 합류했으면(invitedBy) 초대 보상 — 초대받은 쪽은 여기서, 초대한 쪽은 InviteRewardOwed 로 그 사람
     * Inventory 트랜잭션에서. 대상 판단(셀프·재가입·같은 쌍 1회)은 Inventory 가 한다.
     */
    @Transactional
    public void onMemberJoined(MemberJoined event) {
        Inventory inventory = loadLocked(recipientOf(event.explorerId()));
        InventoryChange change = inventory.grantRewards(
            catalog.grantedByThemeCompletions(collectionBooks.completedSets(event.mapId())), event.joinedAt());
        InvitationOutcome invitation = inventory.acceptInvitation(
            new Invitation(explorerIdOrNull(event.invitedBy()), event.mapId(), event.rejoined(), event.joinedAt()),
            catalog.grantedByInvitation(GUEST, event.joinedAt()));
        inventories.save(inventory);
        publish(inventory, change);
        publish(inventory, invitation.change());
        invitation.inviterToReward().ifPresent(inviter -> outbox.append(AGGREGATE, inviter.value(),
            new InviteRewardOwed(inviter.value(), event.explorerId(), event.mapId(), event.joinedAt())));
    }

    /** 초대한 쪽 보상(InviteRewardOwed) — 초대자 Inventory 하나만, 회수 없음. 아이템 단위로 멱등. */
    @Transactional
    public void onInviteRewardOwed(InviteRewardOwed event) {
        Inventory inventory = loadLocked(recipientOf(event.inviterId()));
        InventoryChange change = inventory.grantRewards(catalog.grantedByInvitation(HOST, event.joinedAt()), event.joinedAt());
        inventories.save(inventory);
        publish(inventory, change);
    }

    /**
     * 계정 병합(4단계, aggregate Explorer/into) — 익명 탐험가(from)만 가진 재생 불가 아이템(초대 보상 등)과 초대 기록을 계정
     * 탐험가(into) Inventory 로 옮긴다(QA P3-6). into 하나만 잠가 고치고 from 은 읽기만 한다. 멱등.
     */
    @Transactional
    public void onExplorerMerged(ExplorerMerged event) {
        Inventory into = loadLocked(ExplorerId.of(event.intoExplorerId()));
        Inventory merged = load(ExplorerId.of(event.fromExplorerId()));
        InventoryChange change = into.absorbMerged(merged, event.mergedAt());
        inventories.save(into);
        publish(into, change);
    }

    /** GET /inventory — 탐험가가 없으면 404 EXPLORER_NOT_FOUND. */
    @Transactional(readOnly = true)
    public Inventory view(ExplorerId explorerId) {
        territories.personalMapId(explorerId.value());
        return load(explorerId);
    }

    /** PUT /inventory/{itemId}/favorite — 가방에 있는 아이템만(422 ITEM_NOT_OWNED). */
    public OwnedItem markFavorite(ExplorerId explorerId, String itemId, boolean favorite) {
        territories.personalMapId(explorerId.value());
        return writeTx.execute(status -> {
            Inventory inventory = load(explorerId);
            OwnedItem item = inventory.markFavorite(itemId, favorite, clock.instant());
            inventories.save(inventory);
            return item;
        });
    }

    /**
     * 재생 불가 아이템(초대 보상·합류 시 테마 보상)의 받는 사람(4단계 QA N1, 5단계 처리): 병합돼 비활성인 탐험가(from) 앞으로 병합 뒤에
     * 도착한 지급은 계정 탐험가(into)에게 — 병합 흡수(ExplorerMerged)와 레인이 달라 순서가 보장되지 않아도 비활성 from 가방에 남지 않는다.
     * 같은 쌍 1회·셀프 초대 판단은 into 의 Inventory 가 그대로 한다(멱등).
     */
    private ExplorerId recipientOf(String explorerId) {
        return ExplorerId.of(profiles.mergedInto(explorerId).orElse(explorerId));
    }

    /** 이벤트 처리용: 루트 행을 먼저 잠그고 불러온다 — 재계산과 같은 잠금 순서(루트 → 자식). */
    private Inventory loadLocked(ExplorerId explorerId) {
        return inventories.findLocked(explorerId).orElseGet(() -> Inventory.empty(explorerId, clock.instant()));
    }

    private Inventory load(ExplorerId explorerId) {
        return inventories.find(explorerId).orElseGet(() -> Inventory.empty(explorerId, clock.instant()));
    }

    private void publish(Inventory inventory, InventoryChange change) {
        String explorerId = inventory.explorerId().value();
        change.granted().forEach(item -> outbox.append(AGGREGATE, explorerId,
            new ItemGranted(explorerId, item.itemId(), item.source().name(), item.acquiredAt())));
        change.revoked().forEach(item -> outbox.append(AGGREGATE, explorerId,
            new ItemRevoked(explorerId, item.itemId(), change.at())));
    }
}
