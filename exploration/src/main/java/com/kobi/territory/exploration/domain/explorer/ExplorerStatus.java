package com.kobi.territory.exploration.domain.explorer;

/** 탐험가 상태. MERGED = 익명 탐험가가 로그인으로 계정 탐험가에 병합돼 비활성(토큰 무효, 기록은 병합 대상으로 옮겨짐). */
public enum ExplorerStatus {
    ACTIVE, MERGED
}
