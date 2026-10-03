package com.kobi.territory.exploration.domain.explorer;

import java.util.Optional;

/**
 * 로그인 때 무엇을 할지 정하는 규칙(claimExplorer — 사용자 확정 "기존 계정으로 병합").
 * <ul>
 *   <li>계정에 이미 탐험가 B 가 있고, 지금 기기의 익명 탐험가 A 가 B 로 병합 가능 → {@link Kind#MERGE}(A → B)</li>
 *   <li>계정에 B 가 있고 A 가 없거나(또는 A 가 B 자신·비활성·이미 계정 탐험가) → {@link Kind#SIGN_IN}(B 로 로그인만)</li>
 *   <li>계정이 처음이고 A 가 활성 익명 → {@link Kind#LINK}(A 를 계정에 연결, 병합 없음)</li>
 *   <li>계정이 처음이고 연결할 A 가 없음 → {@link Kind#CREATE}(새 탐험가를 만들어 연결)</li>
 * </ul>
 *
 * @param accountExplorer 계정에 연결된 탐험가(SIGN_IN·MERGE), 아니면 null
 * @param current         지금 기기의 익명 탐험가(MERGE·LINK), 아니면 null
 */
public record LoginPlan(Kind kind, Explorer accountExplorer, Explorer current) {

    public enum Kind { SIGN_IN, MERGE, LINK, CREATE }

    public static LoginPlan decide(Optional<Explorer> accountOwner, Optional<Explorer> currentExplorer) {
        if (accountOwner.isPresent()) {
            Explorer owner = accountOwner.get();
            return currentExplorer.filter(current -> current.canMergeInto(owner))
                .map(current -> new LoginPlan(Kind.MERGE, owner, current))
                .orElseGet(() -> new LoginPlan(Kind.SIGN_IN, owner, null));
        }
        return currentExplorer.filter(Explorer::linkable)
            .map(current -> new LoginPlan(Kind.LINK, null, current))
            .orElseGet(() -> new LoginPlan(Kind.CREATE, null, null));
    }
}
