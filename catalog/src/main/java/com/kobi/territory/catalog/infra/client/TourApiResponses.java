package com.kobi.territory.catalog.infra.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.catalog.domain.lineup.Attraction;
import com.kobi.territory.catalog.domain.lineup.FetchFailure;
import com.kobi.territory.catalog.domain.lineup.Festival;
import com.kobi.territory.catalog.domain.region.GeoPoint;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TourAPI(KorService2 searchFestival2 행사정보조회 · searchKeyword2 키워드 검색) 응답 읽기. 두 오퍼레이션의 응답 봉투는 같다(키워드 검색 항목에는
 * 행사 기간이 없다 — contentid·title·mapx·mapy·addr1 만 쓴다). 공공데이터포털 응답의 버릇을 여기서 흡수한다.
 * <ul>
 *   <li>정상: {@code {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[…]},"numOfRows":…,
 *       "pageNo":…,"totalCount":…}}}} — 결과가 없으면 {@code "items": ""}(빈 문자열), 한 건이면 item 이 배열이 아닌 객체일 수 있다.
 *       숫자 필드는 문자열 또는 숫자</li>
 *   <li>인증·한도 오류는 {@code _type=json} 이어도 XML: {@code <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
 *       <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg><returnReasonCode>30</returnReasonCode>…}} —
 *       20 접근 거부·30 미등록 키·31 기한 만료·32 미등록 IP·33 서명 안 된 호출 → 키 거절, 22 일일 한도 초과</li>
 *   <li>요청 변수 오류 등은 JSON 헤더(또는 최상위) resultCode ≠ "0000"</li>
 * </ul>
 * 항목: contentid, title, eventstartdate·eventenddate(yyyyMMdd), mapx(경도)·mapy(위도) — 0·빈 값·국내 범위 밖이면 좌표 없음, addr1(주소).
 */
final class TourApiResponses {

    private static final String OK = "0000";
    private static final Set<String> KEY_REJECTED_CODES = Set.of("20", "30", "31", "32", "33");
    private static final String QUOTA_CODE = "22";
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern XML_REASON = Pattern.compile("<returnReasonCode>\\s*(\\d+)\\s*</returnReasonCode>");
    private static final Pattern XML_AUTH_MESSAGE = Pattern.compile("<returnAuthMsg>\\s*([A-Z0-9_ ]{1,80})\\s*</returnAuthMsg>");
    private static final Pattern XML_RESULT_CODE = Pattern.compile("<resultCode>\\s*(\\d+)\\s*</resultCode>");
    private static final Pattern XML_RESULT_MESSAGE = Pattern.compile("<resultMsg>\\s*([A-Za-z0-9_ ]{1,80})\\s*</resultMsg>");
    /** 대한민국 대략 범위(경도 124~132, 위도 33~39) — 밖이면 좌표 오류로 본다. */
    private static final double MIN_LONGITUDE = 124;
    private static final double MAX_LONGITUDE = 132;
    private static final double MIN_LATITUDE = 33;
    private static final double MAX_LATITUDE = 39;

    private TourApiResponses() {}

    sealed interface Parsed permits Page, Failure {}

    /** @param skipped 날짜·id 가 없어 뺀 항목 수, @param attractions 키워드 검색(관광지)으로 읽었을 때만 채운다 */
    record Page(List<Festival> festivals, List<Attraction> attractions, int totalCount, int skipped) implements Parsed {

        /** 쪽에서 읽은 항목 수(뺀 것 포함). */
        int rows() {
            return festivals.size() + attractions.size() + skipped;
        }
    }

    /** 어떤 항목으로 읽을지. */
    enum Kind { FESTIVAL, ATTRACTION }

    /** 행사정보조회(축제)로 읽는다. */
    static Parsed parse(String body, ObjectMapper json) {
        return parse(body, json, Kind.FESTIVAL);
    }

    record Failure(FetchFailure failure, String detail) implements Parsed {}

    static Parsed parse(String body, ObjectMapper json, Kind kind) {
        if (body == null || body.isBlank()) return new Failure(FetchFailure.BAD_RESPONSE, "빈 응답");
        String trimmed = body.strip();
        if (trimmed.startsWith("<")) return xmlFailure(trimmed);
        JsonNode root;
        try {
            root = json.readTree(trimmed);
        } catch (JsonProcessingException notJson) {
            return new Failure(FetchFailure.BAD_RESPONSE, "JSON 이 아님");
        }
        JsonNode header = root.path("response").path("header");
        String resultCode = text(header.path("resultCode"));
        String resultMessage = text(header.path("resultMsg"));
        if (resultCode == null) {
            resultCode = text(root.path("resultCode"));
            resultMessage = text(root.path("resultMsg"));
        }
        if (resultCode == null) return new Failure(FetchFailure.BAD_RESPONSE, "resultCode 없음");
        if (!OK.equals(resultCode)) return codeFailure(resultCode, resultMessage);
        JsonNode responseBody = root.path("response").path("body");
        JsonNode items = responseBody.path("items");
        List<JsonNode> rows = new ArrayList<>();
        if (items.isObject()) {
            JsonNode item = items.path("item");
            if (item.isArray()) item.forEach(rows::add);
            else if (item.isObject()) rows.add(item);
        }
        List<Festival> festivals = new ArrayList<>();
        List<Attraction> attractions = new ArrayList<>();
        int skipped = 0;
        for (JsonNode row : rows) {
            if (kind == Kind.FESTIVAL) {
                Festival festival = festival(row);
                if (festival == null) skipped++;
                else festivals.add(festival);
            } else {
                Attraction attraction = attraction(row);
                if (attraction == null) skipped++;
                else attractions.add(attraction);
            }
        }
        int totalCount = number(responseBody.path("totalCount"), rows.size());
        return new Page(List.copyOf(festivals), List.copyOf(attractions), totalCount, skipped);
    }

    private static Attraction attraction(JsonNode row) {
        String contentId = text(row.path("contentid"));
        String title = text(row.path("title"));
        if (contentId == null || title == null) return null;
        return new Attraction(contentId, title.strip(), point(text(row.path("mapx")), text(row.path("mapy"))), text(row.path("addr1")));
    }

    private static Festival festival(JsonNode row) {
        String contentId = text(row.path("contentid"));
        String title = text(row.path("title"));
        LocalDate start = date(text(row.path("eventstartdate")));
        if (contentId == null || title == null || start == null) return null;
        return new Festival(contentId, title.strip(), start, date(text(row.path("eventenddate"))),
            point(text(row.path("mapx")), text(row.path("mapy"))), text(row.path("addr1")));
    }

    private static GeoPoint point(String mapx, String mapy) {
        if (mapx == null || mapy == null) return null;
        try {
            double longitude = Double.parseDouble(mapx);
            double latitude = Double.parseDouble(mapy);
            if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE || latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) return null;
            return new GeoPoint(longitude, latitude);
        } catch (NumberFormatException notNumber) {
            return null;
        }
    }

    private static LocalDate date(String yyyymmdd) {
        if (yyyymmdd == null) return null;
        try {
            return LocalDate.parse(yyyymmdd.strip(), DATE);
        } catch (DateTimeParseException notDate) {
            return null;
        }
    }

    private static Failure xmlFailure(String xml) {
        Matcher reason = XML_REASON.matcher(xml);
        if (reason.find()) {
            Matcher message = XML_AUTH_MESSAGE.matcher(xml);
            return codeFailure(reason.group(1), message.find() ? message.group(1).strip() : null);
        }
        Matcher resultCode = XML_RESULT_CODE.matcher(xml);
        if (resultCode.find()) {
            Matcher message = XML_RESULT_MESSAGE.matcher(xml);
            String code = resultCode.group(1);
            return OK.equals(code) ? new Failure(FetchFailure.BAD_RESPONSE, "JSON 대신 XML 응답")
                : codeFailure(code, message.find() ? message.group(1).strip() : null);
        }
        return new Failure(FetchFailure.BAD_RESPONSE, "알 수 없는 XML 응답");
    }

    private static Failure codeFailure(String code, String message) {
        String detail = "코드 " + code + (message == null ? "" : " " + message);
        if (QUOTA_CODE.equals(code)) return new Failure(FetchFailure.QUOTA_EXCEEDED, detail);
        if (KEY_REJECTED_CODES.contains(code)) return new Failure(FetchFailure.KEY_REJECTED, detail);
        return new Failure(FetchFailure.BAD_RESPONSE, detail);
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static int number(JsonNode node, int fallback) {
        String value = text(node);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException notNumber) {
            return fallback;
        }
    }
}
