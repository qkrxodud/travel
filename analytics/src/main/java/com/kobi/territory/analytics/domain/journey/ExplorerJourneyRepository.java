package com.kobi.territory.analytics.domain.journey;

import com.kobi.territory.analytics.domain.actor.ExplorerHash;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 여정 저장 포트. 쓰는 쪽은 서버 사실 구독자 하나뿐이다(outbox 릴레이는 한 스레드로 순서대로 전달한다) — 그래서 불러와 바꾸고 저장한다.
 */
public interface ExplorerJourneyRepository {

    Optional<ExplorerJourney> find(ExplorerHash explorerHash);

    void save(ExplorerJourney journey);

    /**
     * 마지막 활동이 원본 보관 기간보다 오래된 여정을 지운다 — before 이전에 가입했거나 가입일을 모르고, 남은 원본 이벤트가 하나도 없는 탐험가.
     * 다시 오면 가입일을 모르는 여정으로 시작한다(코호트에 다시 들어가지 않는다). @return 지운 수
     */
    int purgeInactive(LocalDate before);
}
