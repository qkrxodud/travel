package com.kobi.territory.exploration.api.query;

import java.util.Optional;

/**
 * 탐험가 공개 정보(4단계 — 공유의 공개 프로필 /u/{handle}·카드). handle 은 계정(구글 로그인)이 연결된 활성 탐험가만 가진다.
 */
public interface ExplorerProfileQuery {

    /** handle → explorerId. 입력은 앞뒤 공백·'@' 를 빼고 소문자로 비교한다. 계정 연결된 활성 탐험가만, 없으면 빈 값. */
    Optional<String> explorerIdByHandle(String handle);

    /** explorerId → 현재 handle(소문자). 익명·병합돼 비활성·없는 탐험가면 빈 값. */
    Optional<String> handleOf(String explorerId);

    /** 계정 연결된 활성 탐험가인지(로그인 여부). */
    boolean accountLinked(String explorerId);

    /**
     * 병합돼 비활성인 탐험가(from)면 병합된 계정 탐험가(into) id, 아니면 빈 값(5단계 — 병합 뒤 늦게 도착한 from 앞 이벤트를 into 로
     * 돌릴 때: 소셜 피드 귀속, 꾸미기 초대 보상 N1).
     */
    Optional<String> mergedInto(String explorerId);
}
