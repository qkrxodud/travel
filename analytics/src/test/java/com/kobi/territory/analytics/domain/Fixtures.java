package com.kobi.territory.analytics.domain;

import com.kobi.territory.analytics.domain.actor.ActorKey;
import com.kobi.territory.analytics.domain.actor.DeviceType;
import com.kobi.territory.analytics.domain.actor.ExplorerHash;
import com.kobi.territory.analytics.domain.actor.ExplorerHasher;
import com.kobi.territory.analytics.domain.actor.VisitorId;
import com.kobi.territory.analytics.domain.journey.JourneyPolicy;
import com.kobi.territory.analytics.domain.metrics.MetricsPolicy;
import com.kobi.territory.analytics.domain.tracking.ClientContext;
import com.kobi.territory.analytics.domain.tracking.IngestPolicy;
import com.kobi.territory.analytics.domain.tracking.SubmittedEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 분석 테스트의 등장인물·시각·규칙과 준비 문장(Spring 없음).
 * <p>
 * 화면 이벤트는 {@code 화면("tab_view", "tab", "map")}, 받은 상황은 {@code 휴대폰으로(방문)} 처럼 적는다.
 */
public final class Fixtures {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 2026-10-04 12:00 서울 */
    public static final Instant NOW = Instant.parse("2026-10-04T03:00:00Z");
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    public static final ExplorerHasher HASHER = new ExplorerHasher("test-analytics-salt");
    public static final String EXPLORER_ID = "00000000-0000-0000-0000-00000000000a";
    public static final ExplorerHash EXPLORER = HASHER.hash(EXPLORER_ID);
    public static final VisitorId VISITOR = VisitorId.of("visitor-0001-aaaa");

    public static final IngestPolicy INGEST = new IngestPolicy(50, Duration.ofHours(24), SEOUL);
    public static final JourneyPolicy JOURNEY = new JourneyPolicy(7, 7);
    public static final MetricsPolicy METRICS = new MetricsPolicy(90, 35, 3, 7, 30, 7, 7, 90, 10);

    public static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148";
    public static final String KAKAO_PREVIEW = "facebookexternalhit/1.1; kakaotalk-scrap/1.0";

    private Fixtures() {}

    /** 화면이 보낸 이벤트(시각 없음 — 받은 시각으로). 필드는 키·값 짝. */
    public static SubmittedEvent 화면(String name, Object... keyValues) {
        return 화면_시각(name, null, keyValues);
    }

    public static SubmittedEvent 화면_시각(String name, Instant at, Object... keyValues) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) fields.put((String) keyValues[i], keyValues[i + 1]);
        return new SubmittedEvent(name, at, fields);
    }

    /** 탐험가로 이어지지 않은 방문이 휴대폰으로 보낸 상황(지금 받음). */
    public static ClientContext 휴대폰으로(VisitorId visitor) {
        return new ClientContext(visitor, null, ActorKey.ofVisitor(visitor), DeviceType.MOBILE, null, NOW);
    }

    /** 탐험가 토큰이 있는 방문. */
    public static ClientContext 탐험가로(VisitorId visitor, ExplorerHash explorer) {
        return new ClientContext(visitor, explorer, ActorKey.ofExplorer(explorer), DeviceType.MOBILE, null, NOW);
    }

    public static ClientContext 봇으로(VisitorId visitor) {
        return new ClientContext(visitor, null, ActorKey.ofVisitor(visitor), DeviceType.BOT, null, NOW);
    }

    /** 서울 날짜·시각. */
    public static Instant 서울(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(SEOUL).toInstant();
    }
}
