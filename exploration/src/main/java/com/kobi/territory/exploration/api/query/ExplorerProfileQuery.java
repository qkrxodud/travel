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
}
