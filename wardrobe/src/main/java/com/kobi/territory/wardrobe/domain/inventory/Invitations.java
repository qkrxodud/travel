package com.kobi.territory.wardrobe.domain.inventory;

import com.kobi.territory.common.model.ExplorerId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 일급 컬렉션: 초대 보상 기록(초대받은 쪽 Inventory 가 갖는다 — invite_reward). 초대자마다 한 건 = "같은 쌍 1회".
 * 저장소가 새 행만 insert 하도록 복원 이후 추가분을 기억한다. 기록은 지우지 않는다(보상은 회수 없음, 재계산도 유지).
 */
public final class Invitations {

    private final Map<ExplorerId, InvitationRecord> byInviter = new LinkedHashMap<>();
    private final List<InvitationRecord> added = new ArrayList<>();

    private Invitations(Collection<InvitationRecord> restored) {
        restored.forEach(invitation -> {
            if (byInviter.put(invitation.inviterId(), invitation) != null) {
                throw new IllegalStateException("초대 기록 중복: " + invitation.inviterId());
            }
        });
    }

    public static Invitations empty() {
        return new Invitations(List.of());
    }

    public static Invitations of(Collection<InvitationRecord> restored) {
        return new Invitations(restored);
    }

    /**
     * 초대받은 사람(invitee)의 이번 합류가 보상 대상인지 판단하고, 대상이면 기록한다. 대상 = 초대자가 있고, 재가입이 아니고,
     * 셀프 초대가 아니고, 이 초대자와의 쌍이 처음. @return 새로 기록했으면 그 기록
     */
    Optional<InvitationRecord> accept(ExplorerId invitee, Invitation invitation) {
        ExplorerId inviter = invitation.inviterId();
        if (inviter == null || invitation.rejoined() || inviter.equals(invitee) || byInviter.containsKey(inviter)) {
            return Optional.empty();
        }
        InvitationRecord record = new InvitationRecord(inviter, invitation.mapId(), invitation.joinedAt());
        byInviter.put(inviter, record);
        added.add(record);
        return Optional.of(record);
    }

    /** 병합된 탐험가가 받은 초대 기록을 이어받는다(같은 쌍 1회가 계정에서도 지켜지게). 자기 자신이 초대자인 기록·이미 있는 쌍은 뺀다. */
    void adopt(ExplorerId self, Invitations merged) {
        merged.byInviter.values().stream()
            .filter(invitation -> !invitation.inviterId().equals(self) && !byInviter.containsKey(invitation.inviterId()))
            .forEach(invitation -> {
                byInviter.put(invitation.inviterId(), invitation);
                added.add(invitation);
            });
    }

    public boolean rewardedBy(ExplorerId inviter) {
        return byInviter.containsKey(inviter);
    }

    public int count() {
        return byInviter.size();
    }

    /** 복원 이후 새로 생긴 기록(저장소가 insert). */
    public List<InvitationRecord> added() {
        return List.copyOf(added);
    }
}
