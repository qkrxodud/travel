package com.kobi.territory.analytics.domain.actor;

import java.time.LocalDate;

/**
 * 방문 기록 포트. 처음 본 날·들어온 길·이어진 탐험가는 "비어 있을 때만 채운다"(동시에 같은 방문의 묶음이 와도 한 번만 정해진다 —
 * 어댑터는 없으면 넣고, 비어 있는 칸만 채우는 방식으로 저장한다). 돌려주는 연결 상태로 그 요청의 이벤트를 누구로 셀지 정한다.
 */
public interface VisitorRepository {

    VisitorLink record(VisitorSighting sighting);

    /**
     * 마지막 활동이 원본 보관 기간보다 오래된 방문을 지운다 — before 이전에 처음 봤고 남은 원본 이벤트가 하나도 없는 방문.
     * 다시 오면 새 방문으로 센다. @return 지운 수
     */
    int purgeInactive(LocalDate before);
}
