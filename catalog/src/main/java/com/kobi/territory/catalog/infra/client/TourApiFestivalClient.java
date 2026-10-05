package com.kobi.territory.catalog.infra.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.catalog.application.TourApiSettings;
import com.kobi.territory.catalog.domain.lineup.Attraction;
import com.kobi.territory.catalog.domain.lineup.FestivalFetch;
import com.kobi.territory.catalog.domain.lineup.FestivalSource;
import com.kobi.territory.catalog.domain.lineup.FetchFailure;
import com.kobi.territory.catalog.domain.lineup.FetchUsage;
import com.kobi.territory.catalog.domain.lineup.Festival;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 계절 자료 포트의 TourAPI 구현 — 한국관광공사 국문 관광정보 서비스(KorService2) 행사정보조회 {@code searchFestival2}(축제) +
 * 키워드 검색 {@code searchKeyword2}(관광지 — {@code keyword=…&contentTypeId=12}, 이름 부분 일치).
 * <p>
 * 요청: {@code GET {baseUrl}/searchFestival2?serviceKey=…&MobileOS=ETC&MobileApp=…&_type=json&eventStartDate=yyyyMMdd
 * &eventEndDate=yyyyMMdd&arrange=A&numOfRows=N&pageNo=P} — 기간과 겹쳐 열리는 행사(축제·공연·행사, contentTypeId 15). 제목순(arrange=A)으로
 * 쪽을 넘기는 동안 순서가 흔들리지 않게 한다. 공식 문서: https://www.data.go.kr/data/15101578/openapi.do (활용 매뉴얼 — 행사정보조회).
 * <ul>
 *   <li>키가 없으면 호출하지 않는다(NOT_CONFIGURED). 디코딩 키(원문)는 URL 인코딩해서, 이미 인코딩된 키(%xx 포함)는 그대로 붙인다</li>
 *   <li>같은 요청(서비스 키 제외)을 같은 날(서비스 시간대) 다시 하면 응답 원문 캐시(tourapi_response)를 읽는다 — 관리자 fresh 요청만 건너뛴다</li>
 *   <li>호출 하나하나(재시도 포함)를 하루 호출 수(tourapi_usage, DB — 재기동해도 이어진다)에 먼저 센다. 상한에 닿으면 호출하지 않는다(QUOTA_EXCEEDED,
 *       관리자 경고). 한 쪽을 크게(numOfRows) 잡아 회차 하나를 보통 한 번에 읽는다. 연결 실패·5xx 는 지수 백오프로 몇 번 더, 키 거절·한도 초과는 바로 멈춘다</li>
 *   <li>서비스 키는 로그·오류 메시지·캐시 키에 남기지 않는다(요청 주소를 로그에 찍지 않는다)</li>
 * </ul>
 */
@Component
class TourApiFestivalClient implements FestivalSource {

    private static final Logger log = LoggerFactory.getLogger(TourApiFestivalClient.class);
    private static final String FESTIVALS = "searchFestival2";
    private static final String KEYWORD_SEARCH = "searchKeyword2";
    /** 관광타입 — 관광지(키워드 검색에서 음식점·숙박 등을 거른다). */
    private static final String ATTRACTION_TYPE = "12";
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final TourApiSettings settings;
    private final TourApiResponseStore store;
    private final ObjectMapper json;
    private final Clock clock;
    private final HttpClient http;
    private final TourApiCallCounter counter;

    TourApiFestivalClient(TourApiSettings settings, TourApiResponseStore store, TourApiCallCounter counter, ObjectMapper json, Clock clock) {
        this.settings = settings;
        this.store = store;
        this.counter = counter;
        this.json = json;
        this.clock = clock;
        // 키가 쿼리에 실리므로 리디렉션을 따라가지 않는다(다른 곳으로 키가 가지 않게)
        this.http = HttpClient.newBuilder().connectTimeout(settings.timeout()).followRedirects(HttpClient.Redirect.NEVER).build();
        settings.problem().ifPresentOrElse(
            problem -> log.warn("TourAPI 설정 형식 오류 — 연동을 끄고 기본 목록(AI 추정)을 쓴다: {}", problem),
            () -> log.info("TourAPI 계절 자료: {}", settings.configured() ? "서비스 키 있음 — 계절 회차 후보를 모은다" : "서비스 키 없음 — 기본 목록(AI 추정)을 쓴다"));
    }

    @Override
    public boolean configured() {
        return settings.configured();
    }

    @Override
    public FetchUsage usage() {
        return new FetchUsage(settings.configured(), counter.usedOn(LocalDate.now(clock)), settings.dailyCallLimit(),
            settings.problem().orElse(null));
    }

    @Override
    public FestivalFetch festivalsBetween(LocalDate from, LocalDate until, boolean bypassCache) {
        return read(FESTIVALS, TourApiResponses.Kind.FESTIVAL, page -> "eventStartDate=" + DATE.format(from) + "&eventEndDate="
            + DATE.format(until) + "&arrange=A&numOfRows=" + settings.pageSize() + "&pageNo=" + page, bypassCache);
    }

    /** 키워드 검색(searchKeyword2) — 이름에 키워드가 든 관광지(contentTypeId 12). */
    @Override
    public FestivalFetch attractionsMatching(String keyword, boolean bypassCache) {
        String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        return read(KEYWORD_SEARCH, TourApiResponses.Kind.ATTRACTION, page -> "keyword=" + encoded + "&contentTypeId=" + ATTRACTION_TYPE
            + "&arrange=A&numOfRows=" + settings.pageSize() + "&pageNo=" + page, bypassCache);
    }

    /** 쪽을 넘기며 읽는다(필요한 만큼만 — 다 읽었거나 쪽 수 상한). */
    private FestivalFetch read(String operation, TourApiResponses.Kind kind, IntFunction<String> parametersOfPage, boolean bypassCache) {
        if (!settings.configured()) return new FestivalFetch.Failed(FetchFailure.NOT_CONFIGURED, settings.problem().orElse(""));
        List<Festival> festivals = new ArrayList<>();
        List<Attraction> attractions = new ArrayList<>();
        Instant fetchedAt = null;
        for (int page = 1; ; page++) {
            PageRead read = page(operation, kind, parametersOfPage.apply(page), bypassCache);
            if (read.failure() != null) return read.failure();
            festivals.addAll(read.page().festivals());
            attractions.addAll(read.page().attractions());
            fetchedAt = fetchedAt == null || read.fetchedAt().isBefore(fetchedAt) ? read.fetchedAt() : fetchedAt;
            boolean last = read.page().rows() == 0 || (long) page * settings.pageSize() >= read.page().totalCount();
            if (last) return new FestivalFetch.Fetched(festivals, attractions, fetchedAt, false);
            if (page >= settings.maxPages()) return new FestivalFetch.Fetched(festivals, attractions, fetchedAt, true);
        }
    }

    private PageRead page(String operation, TourApiResponses.Kind kind, String parameters, boolean bypassCache) {
        String requestKey = operation + "?" + parameters;
        if (!bypassCache) {
            Instant startOfToday = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
            Optional<TourApiResponseStore.StoredResponse> cached = store.latest(requestKey, startOfToday);
            if (cached.isPresent() && TourApiResponses.parse(cached.get().body(), json, kind) instanceof TourApiResponses.Page page) {
                return new PageRead(page, cached.get().fetchedAt(), null);
            }
        }
        HttpRequest request;
        try {
            // 요청은 호출 수를 세기 전에 만든다 — 만들 수 없으면(형식 오류) 예산을 쓰지 않고, 예외 메시지(키가 든 주소)를 버린다
            request = request(operation, parameters);
        } catch (IllegalArgumentException malformed) {
            log.warn("TourAPI {} 요청을 만들 수 없음 — 서비스 키·base-url 형식을 확인하세요", operation);
            return PageRead.failed(FetchFailure.NOT_CONFIGURED, "서비스 키·base-url 형식 오류");
        }
        FestivalFetch.Failed lastFailure = null;
        for (int attempt = 0; attempt <= settings.retries(); attempt++) {
            if (attempt > 0 && !pause(attempt)) break;
            if (!counter.tryAcquire(LocalDate.now(clock), settings.dailyCallLimit())) {
                log.warn("TourAPI 오늘 호출 상한({}회)에 닿아 호출하지 않음", settings.dailyCallLimit());
                return PageRead.failed(FetchFailure.QUOTA_EXCEEDED, "이 서비스의 하루 호출 상한 " + settings.dailyCallLimit() + "회에 닿았습니다");
            }
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException unreachable) {
                // 예외 메시지에 요청 주소(키)가 들 수 있어 종류 이름만 남긴다
                lastFailure = new FestivalFetch.Failed(FetchFailure.UNREACHABLE, unreachable.getClass().getSimpleName());
                continue;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return PageRead.failed(FetchFailure.UNREACHABLE, "중단됨");
            }
            int status = response.statusCode();
            if (status >= 500) {
                lastFailure = new FestivalFetch.Failed(FetchFailure.UNREACHABLE, "HTTP " + status);
                continue;
            }
            if (status == 401 || status == 403) return PageRead.failed(FetchFailure.KEY_REJECTED, "HTTP " + status);
            if (status == 429) return PageRead.failed(FetchFailure.QUOTA_EXCEEDED, "HTTP " + status);
            if (status >= 300 && status < 400) return PageRead.failed(FetchFailure.BAD_RESPONSE, "HTTP " + status + "(다른 주소로 보내는 응답은 따라가지 않는다)");
            if (status >= 400) {
                // 그 밖 4xx 는 본문이 오류 XML 일 수 있어 읽어 본다
                TourApiResponses.Parsed parsed = TourApiResponses.parse(response.body(), json, kind);
                if (parsed instanceof TourApiResponses.Failure failure) return PageRead.failed(failure.failure(), failure.detail());
                return PageRead.failed(FetchFailure.BAD_RESPONSE, "HTTP " + status);
            }
            TourApiResponses.Parsed parsed = TourApiResponses.parse(response.body(), json, kind);
            switch (parsed) {
                case TourApiResponses.Failure failure -> {
                    log.warn("TourAPI {} 실패: {} {}", operation, failure.failure(), failure.detail());
                    return PageRead.failed(failure.failure(), failure.detail());
                }
                case TourApiResponses.Page page -> {
                    Instant fetchedAt = clock.instant();
                    store.save(requestKey, fetchedAt, response.body());
                    return new PageRead(page, fetchedAt, null);
                }
            }
        }
        log.warn("TourAPI {} 연결 실패(재시도 {}회 뒤): {}", operation, settings.retries(), lastFailure == null ? "" : lastFailure.detail());
        return new PageRead(null, null, lastFailure == null ? new FestivalFetch.Failed(FetchFailure.UNREACHABLE, "") : lastFailure);
    }

    private HttpRequest request(String operation, String parameters) {
        String url = settings.baseUrl() + "/" + operation + "?serviceKey=" + encodedKey() + "&MobileOS=ETC&MobileApp="
            + URLEncoder.encode(settings.mobileApp(), StandardCharsets.UTF_8) + "&_type=json&" + parameters;
        return HttpRequest.newBuilder(URI.create(url)).timeout(settings.timeout()).header("Accept", "application/json").GET().build();
    }

    /** 공공데이터포털은 인코딩 키(%xx)·디코딩 키(원문)를 함께 준다 — 어느 쪽을 넣어도 한 번만 인코딩되게. */
    private String encodedKey() {
        String key = settings.serviceKey();
        return key.contains("%") ? key : URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    /** 재시도 전 대기(지수 백오프). 중단되면 false. */
    private boolean pause(int attempt) {
        long millis = settings.retryBackoff().toMillis() << Math.min(attempt - 1, 10);
        if (millis <= 0) return true;
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private record PageRead(TourApiResponses.Page page, Instant fetchedAt, FestivalFetch.Failed failure) {
        static PageRead failed(FetchFailure failure, String detail) {
            return new PageRead(null, null, new FestivalFetch.Failed(failure, detail));
        }
    }
}
