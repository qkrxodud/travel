package com.kobi.territory.analytics.application;

import com.kobi.territory.analytics.domain.actor.ExplorerHash;
import com.kobi.territory.analytics.domain.actor.ExplorerHasher;
import com.kobi.territory.analytics.domain.journey.ExplorerJourney;
import com.kobi.territory.analytics.domain.journey.ExplorerJourneyRepository;
import com.kobi.territory.analytics.domain.tracking.EventDefinitions;
import com.kobi.territory.analytics.domain.tracking.InvalidTrackedEvent;
import com.kobi.territory.analytics.domain.tracking.ServerFact;
import com.kobi.territory.analytics.domain.tracking.TrackedEvent;
import com.kobi.territory.analytics.domain.tracking.TrackedEventRepository;
import java.time.LocalDate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 서버 사실 적기(outbox 구독자 {@code analytics.events} 가 부른다 — 릴레이의 구독자 트랜잭션 안). 멱등: 같은 사실은 지문으로 한 번만
 * 적히고, 여정의 날짜들은 처음 한 번만 정해진다. 여정은 이 구독자만 쓴다(릴레이는 한 스레드로 순서대로 전달한다).
 */
@Component
public class ServerFactRecorder {

    private static final Logger log = LoggerFactory.getLogger(ServerFactRecorder.class);

    private final TrackedEventRepository events;
    private final ExplorerJourneyRepository journeys;
    private final AnalyticsSettings settings;
    private final ExplorerHasher hasher;
    private final EventDefinitions definitions = EventDefinitions.standard();

    public ServerFactRecorder(TrackedEventRepository events, ExplorerJourneyRepository journeys, AnalyticsSettings settings) {
        this.events = events;
        this.journeys = journeys;
        this.settings = settings;
        this.hasher = new ExplorerHasher(settings.salt());
    }

    /** 사실 한 줄을 적는다(이미 적힌 사실이면 그대로). */
    public void record(ServerFact fact) {
        append(fact);
    }

    /** 가입 — 여정을 시작한다(가입 사실이 늦게 왔으면 가입일만 채운다). */
    public void recordExplorerCreated(ServerFact fact) {
        append(fact);
        ExplorerJourney journey = journeyOf(fact);
        journey.created(dayOf(fact));
        journeys.save(journey);
    }

    /** 체크인 — 가입일을 아는 탐험가의 처음 체크인이면 첫 체크인 사실도 적는다. */
    public void recordCheckIn(ServerFact fact) {
        append(fact);
        ExplorerJourney journey = journeyOf(fact);
        boolean first = journey.checkedIn(dayOf(fact), settings.journeyPolicy());
        journeys.save(journey);
        if (first) {
            append(new ServerFact(EventDefinitions.FIRST_CHECK_IN, fact.explorerId(), fact.occurredAt(), Map.of(),
                EventDefinitions.FIRST_CHECK_IN + "|" + fact.explorerId()));
        }
    }

    /** 초대(초대코드·프로필 링크)로 공유 지도 합류 — 처음 합류가 가입 직후면 초대 유입. */
    public void recordInvitedJoin(ServerFact fact) {
        append(fact);
        ExplorerJourney journey = journeyOf(fact);
        journey.joinedByInvite(dayOf(fact), settings.journeyPolicy());
        journeys.save(journey);
    }

    /**
     * 정의에 맞지 않는 사실(예: 공개 이벤트 값 형식이 바뀜)은 경고만 남기고 건너뛴다 — 관찰자가 실패해 재시도·FAILED 로 이 지도의 분석 전달을
     * 멈추게 하지 않는다(게임에는 영향 없음).
     */
    private void append(ServerFact fact) {
        ExplorerHash explorerHash = hasher.hash(fact.explorerId());
        try {
            TrackedEvent event = definitions.require(fact.name())
                .server(fact, explorerHash, hasher.fingerprint(fact.naturalKey()), settings.ingestPolicy());
            events.appendOnce(event);
        } catch (InvalidTrackedEvent invalid) {
            log.warn("분석: 정의에 맞지 않는 서버 사실을 건너뜀 — {}", invalid.getMessage());
        }
    }

    private ExplorerJourney journeyOf(ServerFact fact) {
        ExplorerHash explorerHash = hasher.hash(fact.explorerId());
        return journeys.find(explorerHash).orElseGet(() -> ExplorerJourney.unknownStart(explorerHash));
    }

    private LocalDate dayOf(ServerFact fact) {
        return settings.ingestPolicy().dayOf(fact.occurredAt());
    }
}
