package com.kobi.territory.catalog.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.catalog.application.TourApiSettings;
import com.kobi.territory.catalog.domain.lineup.FestivalFetch;
import com.kobi.territory.catalog.domain.lineup.FetchFailure;
import com.kobi.territory.catalog.domain.lineup.Festival;
import com.kobi.territory.catalog.domain.region.RegionLocator;
import com.kobi.territory.catalog.infra.repository.JsonCatalogRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 한국관광공사 TourAPI 행사정보조회 읽기 — 실제 키 없이, 공식 문서 예시 모양으로 녹화한 응답(src/test/resources/tourapi)을 내는 가짜 서버로
 * 확인한다. 하루 호출 예산·같은 날 캐시·키 숨김을 함께 본다.
 */
@DisplayName("TourAPI 축제 자료 읽기")
class TourApiFestivalClientTest {

    private static final String 디코딩_키 = "test+key/with==";
    private static final ZoneId 서울 = ZoneId.of("Asia/Seoul");
    private static final LocalDate 봄_첫날 = LocalDate.of(2027, 3, 6);
    private static final LocalDate 봄_끝날 = LocalDate.of(2027, 5, 14);

    private HttpServer server;
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final MemoryStore store = new MemoryStore();
    private final MemoryCounter counter = new MemoryCounter();
    private MovableClock clock;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/B551011/KorService2/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            requests.add(exchange.getRequestURI().getRawQuery());
            Reply reply = replies.isEmpty() ? new Reply(200, resource("searchFestival2-empty.json")) : replies.poll();
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        clock = new MovableClock(Instant.parse("2027-02-10T00:00:00Z"));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private TourApiFestivalClient 클라이언트(String key, int pageSize, int maxPages, int dailyLimit) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/B551011/KorService2";
        return new TourApiFestivalClient(new TourApiSettings(key, base, "TerritoryTest", Duration.ofSeconds(5), 2, Duration.ZERO, pageSize,
            maxPages, dailyLimit), store, counter, new ObjectMapper(), clock);
    }

    private TourApiFestivalClient 클라이언트() {
        return 클라이언트(디코딩_키, 2, 5, 200);
    }

    private void 응답(int status, String resourceName) {
        replies.add(new Reply(status, resource(resourceName)));
    }

    private void 봄_두_쪽() {
        응답(200, "searchFestival2-spring-page1.json");
        응답(200, "searchFestival2-spring-page2.json");
    }

    private static String resource(String name) {
        try (InputStream in = TourApiFestivalClientTest.class.getResourceAsStream("/tourapi/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Map<String, String> 변수(String rawQuery) {
        Map<String, String> parameters = new HashMap<>();
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            parameters.put(pair.substring(0, equals), pair.substring(equals + 1));
        }
        return parameters;
    }

    @Nested
    @DisplayName("키가 있으면")
    class Configured {

        @Test
        @DisplayName("회차 기간에 열리는 행사를 이름순으로, 한 번에 많이 묻는다")
        void requestShape() {
            응답(200, "searchFestival2-empty.json");
            클라이언트(디코딩_키, 1000, 3, 200).festivalsBetween(봄_첫날, 봄_끝날, false);

            Map<String, String> parameters = 변수(requests.getFirst());
            assertThat(parameters).containsEntry("eventStartDate", "20270306").containsEntry("eventEndDate", "20270514")
                .containsEntry("_type", "json").containsEntry("MobileOS", "ETC").containsEntry("MobileApp", "TerritoryTest")
                .containsEntry("arrange", "A").containsEntry("numOfRows", "1000").containsEntry("pageNo", "1");
        }

        @Test
        @DisplayName("쪽을 넘겨 모두 읽고, 항목 하나짜리 쪽·숫자 좌표·좌표 없는 항목도 읽는다")
        void pages() {
            봄_두_쪽();
            FestivalFetch fetch = 클라이언트().festivalsBetween(봄_첫날, 봄_끝날, false);

            assertThat(fetch).isInstanceOf(FestivalFetch.Fetched.class);
            List<Festival> festivals = ((FestivalFetch.Fetched) fetch).festivals();
            assertThat(festivals).extracting(Festival::contentId).containsExactly("T1", "T2", "T3");
            assertThat(festivals.get(0).location().longitude()).isEqualTo(128.76433);
            assertThat(festivals.get(0).endDate()).isEqualTo(LocalDate.of(2027, 4, 5));
            assertThat(festivals.get(1).location()).isNull();
            assertThat(festivals.get(1).address()).startsWith("충청북도 충주시");
            assertThat(festivals.get(2).location().latitude()).isEqualTo(37.5244);
            assertThat(requests).hasSize(2);
        }

        @Test
        @DisplayName("그 기간에 행사가 하나도 없으면 빈 목록이다")
        void empty() {
            응답(200, "searchFestival2-empty.json");

            assertThat(클라이언트().festivalsBetween(봄_첫날, 봄_끝날, false))
                .isInstanceOfSatisfying(FestivalFetch.Fetched.class, fetched -> assertThat(fetched.festivals()).isEmpty());
        }

        @Test
        @DisplayName("쪽 수 상한에 닿으면 앞쪽만 읽었다고 표시한다")
        void truncated() {
            봄_두_쪽();

            assertThat(클라이언트(디코딩_키, 2, 1, 200).festivalsBetween(봄_첫날, 봄_끝날, false))
                .isInstanceOfSatisfying(FestivalFetch.Fetched.class, fetched -> {
                    assertThat(fetched.truncated()).isTrue();
                    assertThat(fetched.festivals()).hasSize(2);
                });
        }

        @Test
        @DisplayName("공공데이터포털의 원문 키를 넣으면 기관이 받는 키도 원문 그대로다")
        void encodesRawKey() {
            응답(200, "searchFestival2-empty.json");
            클라이언트().festivalsBetween(봄_첫날, 봄_끝날, false);

            assertThat(변수(requests.getFirst())).containsEntry("serviceKey", "test%2Bkey%2Fwith%3D%3D");
        }

        @Test
        @DisplayName("주소용으로 바꿔 둔 키를 넣어도 기관이 받는 키는 같다")
        void keepsEncodedKey() {
            응답(200, "searchFestival2-empty.json");
            클라이언트("test%2Bkey%2Fwith%3D%3D", 2, 5, 200).festivalsBetween(봄_첫날, 봄_끝날, false);

            assertThat(변수(requests.getFirst())).containsEntry("serviceKey", "test%2Bkey%2Fwith%3D%3D");
        }
    }

    @Nested
    @DisplayName("계절 관광지를 찾으면")
    class Attractions {

        @Test
        @DisplayName("계절 관광지는 이름으로 찾되 관광지만, 한 번에 많이 묻는다")
        void requestShape() {
            응답(200, "searchFestival2-empty.json");
            클라이언트(디코딩_키, 1000, 3, 200).attractionsMatching("단풍", false);

            assertThat(paths.getFirst()).endsWith("/searchKeyword2");
            assertThat(변수(requests.getFirst())).containsEntry("keyword", "%EB%8B%A8%ED%92%8D").containsEntry("contentTypeId", "12")
                .containsEntry("_type", "json").containsEntry("arrange", "A").containsEntry("numOfRows", "1000").containsEntry("pageNo", "1");
        }

        @Test
        @DisplayName("실제 응답(2026-10-05 \"단풍\" 녹화 — 본문만)을 기간 없는 관광지로 읽고 우리 시·군·구로 옮긴다")
        void realResponse() {
            응답(200, "searchKeyword2-real-danpung.json");
            FestivalFetch fetch = 클라이언트().attractionsMatching("단풍", false);
            RegionLocator 위치찾기 = new JsonCatalogRepository().load().regionLocator(3);

            assertThat(fetch).isInstanceOfSatisfying(FestivalFetch.Fetched.class, fetched -> {
                assertThat(fetched.festivals()).isEmpty();
                assertThat(fetched.attractions()).hasSize(3);
                assertThat(fetched.attractions().stream()
                    .map(attraction -> 위치찾기.locate(attraction.location(), attraction.address()).orElseThrow().code().value()))
                    .containsExactlyInAnyOrder("KR-35040", "KR-32350", "KR-32050"); // 단풍산: 주소 영월, 좌표 정선 — 좌표를 따른다
            });
        }

        @Test
        @DisplayName("같은 날 같은 검색어는 다시 묻지 않고, 다른 검색어는 따로 묻는다")
        void cachePerKeyword() {
            응답(200, "searchKeyword2-real-danpung.json");
            응답(200, "searchFestival2-empty.json");
            TourApiFestivalClient client = 클라이언트(디코딩_키, 1000, 3, 200);
            client.attractionsMatching("단풍", false);
            client.attractionsMatching("단풍", false);
            client.attractionsMatching("단풍길", false);

            assertThat(requests).hasSize(2);
        }
    }

    @Nested
    @DisplayName("기관이 거절하면")
    class Refused {

        @Test
        @DisplayName("기관이 서비스 키를 모른다고 하면 키 거절이고, 다시 묻지 않으며 알림에 키가 보이지 않는다")
        void keyRejected() {
            응답(200, "error-key-not-registered.xml");
            FestivalFetch fetch = 클라이언트().festivalsBetween(봄_첫날, 봄_끝날, false);

            assertThat(fetch).isInstanceOfSatisfying(FestivalFetch.Failed.class, failed -> {
                assertThat(failed.failure()).isEqualTo(FetchFailure.KEY_REJECTED);
                assertThat(failed.message()).contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR").doesNotContain("test").doesNotContain("key%2F");
            });
            assertThat(requests).hasSize(1);
        }

        @Test
        @DisplayName("기관이 하루 한도를 넘었다고 하면 한도 초과다")
        void quota() {
            응답(200, "error-quota-exceeded.xml");

            assertThat(클라이언트().festivalsBetween(봄_첫날, 봄_끝날, false))
                .isInstanceOfSatisfying(FestivalFetch.Failed.class, failed -> assertThat(failed.failure()).isEqualTo(FetchFailure.QUOTA_EXCEEDED));
        }

        @Test
        @DisplayName("기관이 요청이 잘못됐다고 하면 읽을 수 없는 답으로 본다")
        void badRequest() {
            응답(200, "error-invalid-parameter.json");

            assertThat(클라이언트().festivalsBetween(봄_첫날, 봄_끝날, false))
                .isInstanceOfSatisfying(FestivalFetch.Failed.class, failed -> {
                    assertThat(failed.failure()).isEqualTo(FetchFailure.BAD_RESPONSE);
                    assertThat(failed.detail()).contains("10");
                });
        }

        @Test
        @DisplayName("깨진 답은 읽을 수 없는 답으로 본다")
        void broken() {
            replies.add(new Reply(200, "{\"response\":"));

            assertThat(클라이언트().festivalsBetween(봄_첫날, 봄_끝날, false))
                .isInstanceOfSatisfying(FestivalFetch.Failed.class, failed -> assertThat(failed.failure()).isEqualTo(FetchFailure.BAD_RESPONSE));
        }
    }

    @Nested
    @DisplayName("잠깐 안 되면")
    class Transient {

        @Test
        @DisplayName("서버 오류 뒤에는 다시 불러 읽는다")
        void retries() {
            replies.add(new Reply(503, "busy"));
            응답(200, "searchFestival2-empty.json");

            assertThat(클라이언트().festivalsBetween(봄_첫날, 봄_끝날, false)).isInstanceOf(FestivalFetch.Fetched.class);
            assertThat(requests).hasSize(2);
        }

        @Test
        @DisplayName("계속 안 되면 정해진 횟수만 더 해 보고 연결 실패로 끝낸다")
        void givesUp() {
            for (int i = 0; i < 5; i++) replies.add(new Reply(502, "down"));

            assertThat(클라이언트().festivalsBetween(봄_첫날, 봄_끝날, false))
                .isInstanceOfSatisfying(FestivalFetch.Failed.class, failed -> assertThat(failed.failure()).isEqualTo(FetchFailure.UNREACHABLE));
            assertThat(requests).hasSize(3);
        }
    }

    @Nested
    @DisplayName("하루 호출 예산")
    class Budget {

        @Test
        @DisplayName("같은 날 같은 요청은 다시 부르지 않고 저장해 둔 응답을 읽는다")
        void sameDayCache() {
            봄_두_쪽();
            TourApiFestivalClient client = 클라이언트();
            client.festivalsBetween(봄_첫날, 봄_끝날, false);
            FestivalFetch again = client.festivalsBetween(봄_첫날, 봄_끝날, false);

            assertThat(requests).hasSize(2);
            assertThat(again).isInstanceOfSatisfying(FestivalFetch.Fetched.class, fetched -> assertThat(fetched.festivals()).hasSize(3));
            assertThat(client.usage().callsToday()).isEqualTo(2);
        }

        @Test
        @DisplayName("다음 날이 되거나 새로 읽기를 고르면 다시 부른다")
        void refreshes() {
            봄_두_쪽();
            TourApiFestivalClient client = 클라이언트();
            client.festivalsBetween(봄_첫날, 봄_끝날, false);
            봄_두_쪽();
            client.festivalsBetween(봄_첫날, 봄_끝날, true);
            clock.advance(Duration.ofDays(1));
            client.festivalsBetween(봄_첫날, 봄_끝날, false);

            assertThat(requests).hasSize(5);
        }

        @Test
        @DisplayName("상한에 닿으면 그날은 더 부르지 않고 한도 초과로 알린다")
        void stopsAtLimit() {
            봄_두_쪽();
            TourApiFestivalClient client = 클라이언트(디코딩_키, 2, 5, 1);
            FestivalFetch fetch = client.festivalsBetween(봄_첫날, 봄_끝날, false);

            assertThat(fetch).isInstanceOfSatisfying(FestivalFetch.Failed.class, failed -> {
                assertThat(failed.failure()).isEqualTo(FetchFailure.QUOTA_EXCEEDED);
                assertThat(failed.detail()).contains("하루 호출 상한 1회");
            });
            assertThat(requests).hasSize(1);
            assertThat(client.usage().exhausted()).isTrue();
        }

        @Test
        @DisplayName("다시 해 보는 호출도 예산에서 센다")
        void retriesCount() {
            replies.add(new Reply(503, "busy"));
            응답(200, "searchFestival2-empty.json");
            TourApiFestivalClient client = 클라이언트();
            client.festivalsBetween(봄_첫날, 봄_끝날, false);

            assertThat(client.usage().callsToday()).isEqualTo(2);
        }

        @Test
        @DisplayName("다음 날이면 예산이 새로 시작한다")
        void nextDay() {
            TourApiFestivalClient client = 클라이언트(디코딩_키, 2, 5, 1);
            응답(200, "searchFestival2-empty.json");
            client.festivalsBetween(봄_첫날, 봄_끝날, true);
            clock.advance(Duration.ofDays(1));

            assertThat(client.usage().callsToday()).isZero();
            응답(200, "searchFestival2-empty.json");
            assertThat(client.festivalsBetween(봄_첫날, 봄_끝날, true)).isInstanceOf(FestivalFetch.Fetched.class);
        }
    }

    @Test
    @DisplayName("실제 TourAPI 응답(2026 봄 일부 녹화 — 열쇠 없이 본문만)도 읽고, 축제 좌표를 우리 시·군·구로 옮긴다")
    void realResponseSample() {
        응답(200, "searchFestival2-real-2026-spring-sample.json");
        FestivalFetch fetch = 클라이언트().festivalsBetween(LocalDate.of(2026, 3, 6), LocalDate.of(2026, 5, 14), false);
        RegionLocator 위치찾기 = new JsonCatalogRepository().load().regionLocator(3);

        assertThat(fetch).isInstanceOfSatisfying(FestivalFetch.Fetched.class, fetched -> {
            assertThat(fetched.festivals()).hasSize(4).allMatch(festival -> festival.location() != null);
            assertThat(fetched.festivals().stream().filter(festival -> festival.title().contains("벚꽃"))
                .map(festival -> 위치찾기.locate(festival.location(), festival.address()).orElseThrow().code().value()))
                .containsExactlyInAnyOrder("KR-32030", "KR-36330", "KR-11240");
        });
    }

    @Nested
    @DisplayName("키·주소 형식이 틀리면")
    class Malformed {

        private TourApiFestivalClient 클라이언트(String key, String base) {
            return new TourApiFestivalClient(new TourApiSettings(key, base, "TerritoryTest", Duration.ofSeconds(5), 2, Duration.ZERO, 1000, 3,
                200), store, counter, new ObjectMapper(), clock);
        }

        private String 주소() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/B551011/KorService2";
        }

        @Test
        @DisplayName("끝이 잘린 키면 연동을 끄고 아무것도 묻지 않으며 하루 예산도 쓰지 않는다 — 알림에 키가 없다")
        void truncatedKey() {
            TourApiFestivalClient client = 클라이언트("LEAKMARK0123%3", 주소());

            FestivalFetch fetch = client.festivalsBetween(봄_첫날, 봄_끝날, true);

            assertThat(client.configured()).isFalse();
            assertThat(fetch).isInstanceOfSatisfying(FestivalFetch.Failed.class, failed -> {
                assertThat(failed.failure()).isEqualTo(FetchFailure.NOT_CONFIGURED);
                assertThat(failed.message()).contains("서비스 키 형식 오류").doesNotContain("LEAKMARK");
            });
            assertThat(requests).isEmpty();
            assertThat(client.usage().callsToday()).isZero();
            assertThat(client.usage().settingsProblem()).doesNotContain("LEAKMARK");
        }

        @Test
        @DisplayName("공백이 섞인 서비스 주소면 연동을 끄고 아무것도 묻지 않는다")
        void spacedBaseUrl() {
            TourApiFestivalClient client = 클라이언트("LEAKMARK0123", " http://127.0.0.1:1/Kor Service2");

            assertThat(client.attractionsMatching("단풍", true))
                .isInstanceOfSatisfying(FestivalFetch.Failed.class, failed -> assertThat(failed.message()).contains("base-url 형식 오류")
                    .doesNotContain("LEAKMARK"));
            assertThat(requests).isEmpty();
            assertThat(client.usage().callsToday()).isZero();
        }

        @Test
        @DisplayName("공백·한글이 섞인 키도 연동을 끈다")
        void invalidCharacters() {
            assertThat(클라이언트("LEAK MARK", 주소()).configured()).isFalse();
            assertThat(클라이언트("키값", 주소()).configured()).isFalse();
            assertThat(클라이언트("LEAKMARK%2B%3D", 주소()).configured()).isTrue();
        }
    }

    @Test
    @DisplayName("다른 주소로 보내는 답은 따라가지 않는다(키가 다른 곳으로 가지 않게)")
    void noRedirect() {
        replies.add(new Reply(302, ""));

        assertThat(클라이언트().festivalsBetween(봄_첫날, 봄_끝날, true))
            .isInstanceOfSatisfying(FestivalFetch.Failed.class, failed -> assertThat(failed.failure()).isEqualTo(FetchFailure.BAD_RESPONSE));
        assertThat(requests).hasSize(1);
    }

    @Test
    @DisplayName("하루 호출 상한은 1 ~ 900 회로만 정할 수 있다(기관 한도 아래)")
    void dailyLimitRange() {
        assertThatThrownBy(() -> new TourApiSettings("key", "https://example.test", "app", Duration.ofSeconds(1), 0,
            Duration.ZERO, 10, 1, 901)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TourApiSettings("key", "https://example.test", "app", Duration.ofSeconds(1), 0,
            Duration.ZERO, 10, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new TourApiSettings("key", "https://example.test", "app", Duration.ofSeconds(1), 0, Duration.ZERO, 10, 1, 900).configured())
            .isTrue();
    }

    @Test
    @DisplayName("키가 없으면 아무것도 부르지 않는다")
    void notConfigured() {
        TourApiFestivalClient client = 클라이언트("", 2, 5, 200);

        assertThat(client.configured()).isFalse();
        assertThat(client.festivalsBetween(봄_첫날, 봄_끝날, true))
            .isInstanceOfSatisfying(FestivalFetch.Failed.class, failed -> assertThat(failed.failure()).isEqualTo(FetchFailure.NOT_CONFIGURED));
        assertThat(requests).isEmpty();
    }

    @Test
    @DisplayName("설정값을 글로 찍어도 키는 보이지 않는다")
    void settingsHideKey() {
        assertThat(new TourApiSettings(디코딩_키, "https://example.test", "app", Duration.ofSeconds(1), 0, Duration.ZERO, 10, 1, 10)
            .toString()).doesNotContain("test+key").contains("***");
    }

    private record Reply(int status, String body) {}

    private static final class MemoryStore implements TourApiResponseStore {
        private final List<Object[]> rows = new ArrayList<>();

        @Override
        public Optional<StoredResponse> latest(String requestKey, Instant notBefore) {
            return rows.reversed().stream().filter(row -> row[0].equals(requestKey) && !((Instant) row[1]).isBefore(notBefore))
                .findFirst().map(row -> new StoredResponse((Instant) row[1], (String) row[2]));
        }

        @Override
        public void save(String requestKey, Instant fetchedAt, String body) {
            assertThat(requestKey).doesNotContain("serviceKey");
            rows.add(new Object[] {requestKey, fetchedAt, body});
        }
    }

    private static final class MemoryCounter implements TourApiCallCounter {
        private final Map<LocalDate, Integer> calls = new HashMap<>();

        @Override
        public synchronized boolean tryAcquire(LocalDate day, int dailyLimit) {
            int used = calls.getOrDefault(day, 0);
            if (used >= dailyLimit) return false;
            calls.put(day, used + 1);
            return true;
        }

        @Override
        public synchronized int usedOn(LocalDate day) {
            return calls.getOrDefault(day, 0);
        }
    }

    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return 서울;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
