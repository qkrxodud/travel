package com.kobi.territory.analytics.application;

import com.kobi.territory.analytics.domain.actor.ActorKey;
import com.kobi.territory.analytics.domain.actor.DeviceType;
import com.kobi.territory.analytics.domain.actor.EntryPoint;
import com.kobi.territory.analytics.domain.actor.ExplorerHash;
import com.kobi.territory.analytics.domain.actor.ExplorerHasher;
import com.kobi.territory.analytics.domain.actor.VisitorId;
import com.kobi.territory.analytics.domain.actor.VisitorLink;
import com.kobi.territory.analytics.domain.actor.VisitorRepository;
import com.kobi.territory.analytics.domain.actor.VisitorSighting;
import com.kobi.territory.analytics.domain.tracking.ClientContext;
import com.kobi.territory.analytics.domain.tracking.EventBatch;
import com.kobi.territory.analytics.domain.tracking.EventDefinitions;
import com.kobi.territory.analytics.domain.tracking.ServerFact;
import com.kobi.territory.analytics.domain.tracking.SubmittedEvent;
import com.kobi.territory.analytics.domain.tracking.TrackedEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * local 전용 시드(개발자가 지표 화면을 바로 볼 수 있게 — {@code POST /dev/analytics/seed}). 지난 days 일 동안의 가짜 방문·가입·체크인·
 * 재방문·초대 합류·오류 토스트·카드 열람을 실제 수집 경로(검증·방문 연결·서버 사실 기록)로 적는다. 난수 씨앗이 같으면 같은 데이터.
 * 탐험가는 실제 탐험가가 아니다(분석 저장소에만 있는 가짜 해시) — 게임 데이터는 건드리지 않는다.
 */
@Service
public class AnalyticsSeedService {

    private static final List<String> TABS = List.of("map", "bag", "sets", "quests", "rank", "profile");
    private static final List<String> ERROR_CODES = List.of("DAILY_CAP_EXCEEDED", "DUPLICATE_VISIT", "INVITE_CODE_NOT_FOUND",
        "MAP_FULL", "NETWORK_ERROR");
    private static final List<String> CARD_KINDS = List.of("territory", "recent", "recap");

    private final VisitorRepository visitors;
    private final TrackedEventRepository events;
    private final ServerFactRecorder facts;
    private final AnalyticsSettings settings;
    private final ExplorerHasher hasher;
    private final EventDefinitions definitions = EventDefinitions.standard();
    private final Clock clock;

    public AnalyticsSeedService(VisitorRepository visitors, TrackedEventRepository events, ServerFactRecorder facts,
                                AnalyticsSettings settings, Clock clock) {
        this.visitors = visitors;
        this.events = events;
        this.facts = facts;
        this.settings = settings;
        this.hasher = new ExplorerHasher(settings.salt());
        this.clock = clock;
    }

    /** @return 만든 방문 수·탐험가 수 */
    @Transactional
    public SeedResult seed(int days, int visitorsPerDay, long randomSeed) {
        Random random = new Random(randomSeed);
        ZoneId zone = settings.ingestPolicy().zone();
        LocalDate today = settings.ingestPolicy().dayOf(clock.instant());
        int visitorCount = 0;
        int explorerCount = 0;
        for (int back = days - 1; back >= 0; back--) {
            LocalDate day = today.minusDays(back);
            int newcomers = Math.max(1, visitorsPerDay + random.nextInt(visitorsPerDay / 2 + 1) - visitorsPerDay / 4);
            for (int i = 0; i < newcomers; i++) {
                visitorCount++;
                String visitorKey = "seed-" + day + "-" + i;
                EntryPoint entry = entry(random);
                boolean becomesExplorer = random.nextDouble() < 0.6;
                String explorerId = becomesExplorer ? "seed-explorer-" + randomSeed + "-" + day + "-" + i : null;
                Instant firstSeen = at(day, 9 + random.nextInt(12), random, zone);
                visit(visitorKey, explorerId, firstSeen, List.of(
                    event(EventDefinitions.APP_OPEN, Map.of("entry", entry.label())),
                    event(EventDefinitions.TAB_VIEW, Map.of("tab", "map"))));
                if (!becomesExplorer) continue;
                explorerCount++;
                facts.recordExplorerCreated(fact(EventDefinitions.EXPLORER_CREATED, explorerId, firstSeen, Map.of(), "created"));
                live(explorerId, visitorKey, day, today, random, zone);
            }
            publicViews(day, random, zone);
        }
        return new SeedResult(days, visitorCount, explorerCount);
    }

    /** 가입한 뒤의 나날 — 첫 체크인, 재방문(날이 갈수록 드물게), 초대 합류, 오류 토스트. */
    private void live(String explorerId, String visitorKey, LocalDate joined, LocalDate today, Random random, ZoneId zone) {
        int firstCheckInAfter = random.nextDouble() < 0.7 ? random.nextInt(3) : -1;
        if (random.nextDouble() < 0.08) {
            Instant at = at(joined.plusDays(Math.min(random.nextInt(4), today.toEpochDay() - joined.toEpochDay())), 20, random, zone);
            facts.recordInvitedJoin(fact(EventDefinitions.SHARED_MAP_JOINED, explorerId, at,
                Map.of("via", random.nextBoolean() ? "invite_code" : "profile_link", "rejoined", false), "join"));
        }
        for (LocalDate day = joined; !day.isAfter(today); day = day.plusDays(1)) {
            long age = day.toEpochDay() - joined.toEpochDay();
            boolean active = age == 0 || random.nextDouble() < 0.45 / Math.sqrt(age);
            if (!active) continue;
            Instant at = at(day, 18 + random.nextInt(4), random, zone);
            List<SubmittedEvent> screen = new ArrayList<>(List.of(event(EventDefinitions.TAB_VIEW,
                Map.of("tab", TABS.get(random.nextInt(TABS.size()))))));
            if (age > 0) screen.add(0, event(EventDefinitions.APP_OPEN, Map.of("entry", "direct")));
            if (random.nextDouble() < 0.1) {
                screen.add(event(EventDefinitions.ERROR_TOAST, Map.of("code", ERROR_CODES.get(random.nextInt(ERROR_CODES.size())))));
            }
            if (firstCheckInAfter >= 0 && age >= firstCheckInAfter && random.nextDouble() < (age == firstCheckInAfter ? 1 : 0.5)) {
                screen.add(event(EventDefinitions.CHECKIN_OPEN, Map.of()));
                screen.add(event(EventDefinitions.CHECKIN_SAVE, Map.of()));
                facts.recordCheckIn(fact(EventDefinitions.CHECK_IN, explorerId, at, Map.of("rarity", "common"), "visit-" + day));
            }
            if (random.nextDouble() < 0.05) screen.add(event(EventDefinitions.SHARE_CLICK, Map.of("target", "map")));
            visit(visitorKey, explorerId, at, screen);
        }
    }

    private void publicViews(LocalDate day, Random random, ZoneId zone) {
        int views = random.nextInt(8);
        for (int i = 0; i < views; i++) {
            boolean bot = random.nextDouble() < 0.3;
            String kind = CARD_KINDS.get(random.nextInt(CARD_KINDS.size()));
            events.appendAll(List.of(definitions.require(EventDefinitions.CARD_VIEW).request(Map.of("kind", kind),
                bot ? DeviceType.BOT : DeviceType.MOBILE, null, at(day, 12, random, zone), settings.ingestPolicy())));
        }
        if (views > 0) {
            events.appendAll(List.of(definitions.require(EventDefinitions.PROFILE_VIEW).request(Map.of(), DeviceType.MOBILE, null,
                at(day, 13, random, zone), settings.ingestPolicy())));
        }
    }

    /** 실제 수집 경로와 같게: 방문 기록·연결 → 이벤트 검증·적기 → 처음 이어지면 예전 이벤트 다시 묶기. */
    private void visit(String visitorKey, String explorerId, Instant at, List<SubmittedEvent> submitted) {
        VisitorId visitorId = VisitorId.of(visitorKey);
        ExplorerHash explorerHash = explorerId == null ? null : hasher.hash(explorerId);
        EventBatch batch = EventBatch.of(submitted, settings.ingestPolicy());
        VisitorLink link = batch.sighting(definitions, visitorId, explorerHash, DeviceType.MOBILE, at).map(visitors::record).orElse(VisitorLink.NONE);
        ActorKey actor = link.actorFor(visitorId, explorerHash);
        events.appendAll(batch.accept(definitions, new ClientContext(visitorId, explorerHash, actor, DeviceType.MOBILE, null, at))
            .accepted());
        link.relinkTarget().ifPresent(explorerActor -> events.relinkVisitor(visitorId, explorerActor));
    }

    private static EntryPoint entry(Random random) {
        double roll = random.nextDouble();
        if (roll < 0.68) return EntryPoint.DIRECT;
        if (roll < 0.80) return EntryPoint.CARD;
        if (roll < 0.88) return EntryPoint.PROFILE;
        if (roll < 0.96) return EntryPoint.INVITE;
        return EntryPoint.OTHER;
    }

    private static SubmittedEvent event(String name, Map<String, Object> fields) {
        return new SubmittedEvent(name, null, fields);
    }

    private static ServerFact fact(String name, String explorerId, Instant at, Map<String, Object> fields, String key) {
        return new ServerFact(name, explorerId, at, fields, "seed|" + name + "|" + explorerId + "|" + key);
    }

    private static Instant at(LocalDate day, int hour, Random random, ZoneId zone) {
        return day.atTime(LocalTime.of(hour, random.nextInt(60))).atZone(zone).toInstant();
    }

    /** 시드 결과. */
    public record SeedResult(int days, int visitors, int explorers) {}
}
