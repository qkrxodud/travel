package com.kobi.territory.wardrobe.application;

import com.kobi.territory.common.event.EventSubscriber;
import com.kobi.territory.exploration.api.event.ExplorerMerged;
import com.kobi.territory.exploration.api.event.MapCreated;
import com.kobi.territory.exploration.api.event.MemberJoined;
import com.kobi.territory.exploration.api.event.RegionVisited;
import com.kobi.territory.exploration.api.event.VisitCancelled;
import com.kobi.territory.progression.api.event.ProvinceConquered;
import com.kobi.territory.progression.api.event.SetCompleted;
import com.kobi.territory.progression.api.event.StreakMilestoneReached;
import com.kobi.territory.wardrobe.api.event.InviteRewardOwed;
import com.kobi.territory.wardrobe.api.event.ItemGranted;
import com.kobi.territory.wardrobe.api.event.ItemRevoked;
import com.kobi.territory.wardrobe.api.event.ThemeRewardOwed;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 꾸미기 컨텍스트의 outbox 구독(§3 체크인 이벤트 흐름). 소비 애그리거트마다 구독자 하나(QA P1-1) — 같은 지도(aggregate)의
 * 체크인·취소·세트 완성·합류가 한 줄로 순서대로 도착한다. id 는 outbox_delivery.subscriber 키라 바꾸지 않는다.
 * 탈퇴(MemberLeft·VisitsHidden)는 구독하지 않는다 — 지역 아이템은 "방문 취소"로만 회수한다(explorer_region 처럼 탈퇴로 줄지 않음, §5).
 */
@Configuration
public class WardrobeSubscriptions {

    /**
     * Inventory: 체크인(지급)·취소(근거가 모두 사라지면 회수)·세트 완성(수령자에게 세트 배경, 완성 직후 합류자에게는 ThemeRewardOwed)·ThemeRewardOwed(그 멤버에게 세트 배경)·지도 합류(이미 완성된
     * 세트 배경 + 초대받은 첫 합류면 초대 보상 — 초대자 몫은 InviteRewardOwed)·지도 생성(개인 지도면 루트 행 선생성)·계정 병합(익명 탐험가의 재생 불가 아이템·초대 기록 이전)·
     * 시·도 정복과 연속 탐험 마일스톤(8단계 — 한정 아이템, 회수 없음).
     */
    @Bean
    EventSubscriber inventorySubscriber(InventoryService inventories) {
        return EventSubscriber.named("wardrobe.inventory")
            .on(RegionVisited.class, inventories::onRegionVisited)
            .on(VisitCancelled.class, inventories::onVisitCancelled)
            .on(SetCompleted.class, inventories::onSetCompleted)
            .on(MemberJoined.class, inventories::onMemberJoined)
            .on(MapCreated.class, inventories::onMapCreated)
            .on(ThemeRewardOwed.class, inventories::onThemeRewardOwed)
            .on(InviteRewardOwed.class, inventories::onInviteRewardOwed)
            .on(ExplorerMerged.class, inventories::onExplorerMerged)
            .on(ProvinceConquered.class, inventories::onProvinceConquered)
            .on(StreakMilestoneReached.class, inventories::onStreakMilestoneReached)
            .build();
    }

    /** Scene: 획득(자동 착용)·회수(벗김). aggregate = Inventory(explorerId) 한 줄이라 획득·회수 순서가 지켜진다. */
    @Bean
    EventSubscriber sceneSubscriber(SceneService scenes) {
        return EventSubscriber.named("wardrobe.scene")
            .on(ItemGranted.class, scenes::onItemGranted)
            .on(ItemRevoked.class, scenes::onItemRevoked)
            .build();
    }
}
