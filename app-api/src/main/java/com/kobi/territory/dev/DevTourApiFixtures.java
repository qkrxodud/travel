package com.kobi.territory.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 개발용 가짜 TourAPI(행사정보조회 searchFestival2) 응답 — 실제 키 없이 "키 있는 흐름"을 로컬·통합 테스트에서 돌린다. 응답 모양은 공공데이터포털
 * KorService2 문서 예시와 2026-10-05 실제 응답(항목 필드 29개, 항목 값은 문자열·numOfRows·totalCount 는 숫자)을 따른다(결과 없으면
 * {@code "items": ""}, 키·한도 오류는 XML). 축제는 <b>가짜</b>다 — 이름 앞에
 * "[개발용]"을 붙여 실제 사실처럼 보이지 않게 한다. 좌표는 우리 경계 안쪽 점.
 * <ul>
 *   <li>요청 기간(eventStartDate~eventEndDate)이 걸친 해마다 같은 월·일로 축제를 만들고, 기간과 겹치는 것만 낸다(제목순, numOfRows·pageNo)</li>
 *   <li>서비스 키 {@code dev-key-rejected} → XML 30(SERVICE_KEY_IS_NOT_REGISTERED_ERROR), {@code dev-quota} → XML 22(한도 초과),
 *       {@code dev-broken} → 깨진 본문</li>
 *   <li>벚꽃: 열두 지역(진해·경주 두 건씩, 춘천·충주(주소만)·울산 남구 등 기본 목록 밖 포함) + 테마 밖 1건 + 지역을 못 찾는 1건.
 *       단풍: 여섯 지역(축제)</li>
 *   <li>키워드 검색(searchKeyword2, 관광지): "단풍" → 정읍(축제 지역과 겹침)·영월·태백·보은(주소만)·합천 — 단풍은 축제 여섯 + 관광지만 넷으로 열 곳이
 *       찬다. "벚꽃" → 제천(관광지만 — 축제 지역 열 곳보다 뒤라 들지 않는다). 키워드는 이름 부분 일치</li>
 * </ul>
 */
public final class DevTourApiFixtures {

    public static final String KEY_REJECTED = "dev-key-rejected";
    public static final String KEY_QUOTA = "dev-quota";
    public static final String KEY_BROKEN = "dev-broken";
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final List<Fixture> FIXTURES = List.of(
        new Fixture("900001", "[개발용] 진해 벚꽃 축제", "03-27", "04-05", "128.76433", "35.137", "경상남도 창원시 진해구 (가짜 주소)"),
        new Fixture("900002", "[개발용] 진해 벚꽃 야행", "04-01", "04-03", "128.76433", "35.137", "경상남도 창원시 진해구 (가짜 주소)"),
        new Fixture("900003", "[개발용] 경주 벚꽃 마라톤", "04-05", "04-05", "129.22767", "35.84533", "경상북도 경주시 (가짜 주소)"),
        new Fixture("900004", "[개발용] 보문 벚꽃 축제", "03-30", "04-06", "129.22767", "35.84533", "경상북도 경주시 (가짜 주소)"),
        new Fixture("900005", "[개발용] 여의도 봄꽃(벚꽃) 축제", "04-04", "04-13", "126.9092", "37.5244", "서울특별시 영등포구 (가짜 주소)"),
        new Fixture("900006", "[개발용] 석촌호수 벚꽃 축제", "04-01", "04-07", "127.12027", "37.505", "서울특별시 송파구 (가짜 주소)"),
        new Fixture("900007", "[개발용] 하동 십리 벚꽃길 축제", "03-29", "04-01", "127.7942", "35.1406", "경상남도 하동군 (가짜 주소)"),
        new Fixture("900008", "[개발용] 경포 벚꽃 축제", "04-03", "04-09", "128.8605", "37.7105", "강원특별자치도 강릉시 (가짜 주소)"),
        new Fixture("900009", "[개발용] 은파 벚꽃 축제", "04-05", "04-06", "126.80487", "35.9675", "전북특별자치도 군산시 (가짜 주소)"),
        new Fixture("900010", "[개발용] 제주 왕벚꽃 축제", "03-24", "03-26", "126.51193", "33.43903", "제주특별자치도 제주시 (가짜 주소)"),
        new Fixture("900011", "[개발용] 구례 벚꽃 축제", "03-28", "03-30", "127.4965", "35.24743", "전라남도 구례군 (가짜 주소)"),
        new Fixture("900012", "[개발용] 춘천 벚꽃 축제", "04-12", "04-14", "127.73307", "37.90663", "강원특별자치도 춘천시 (가짜 주소)"),
        new Fixture("900013", "[개발용] 충주 벚꽃 축제", "04-10", "04-12", "", "", "충청북도 충주시 (가짜 주소)"),
        new Fixture("900014", "[개발용] 울산 벚꽃 축제", "04-02", "04-03", "129.33453", "35.5186", "울산광역시 남구 (가짜 주소)"),
        new Fixture("900015", "[개발용] 봄 딸기 축제", "03-25", "03-30", "126.9092", "37.5244", "서울특별시 영등포구 (가짜 주소)"),
        new Fixture("900016", "[개발용] 바다 위 벚꽃 축제", "04-01", "04-02", "125.0", "34.0", ""),
        new Fixture("900101", "[개발용] 설악 단풍 축제", "10-15", "10-20", "128.54387", "38.18247", "강원특별자치도 속초시 (가짜 주소)"),
        new Fixture("900102", "[개발용] 내장산 단풍 축제", "10-25", "11-05", "126.90267", "35.6204", "전북특별자치도 정읍시 (가짜 주소)"),
        new Fixture("900103", "[개발용] 오대산 단풍 축제", "10-10", "10-12", "128.43503", "37.56247", "강원특별자치도 평창군 (가짜 주소)"),
        new Fixture("900104", "[개발용] 주왕산 단풍 축제", "10-20", "10-26", "129.0906", "36.3775", "경상북도 청송군 (가짜 주소)"),
        new Fixture("900105", "[개발용] 백양사 단풍 축제", "10-28", "11-02", "126.7896", "35.32913", "전라남도 장성군 (가짜 주소)"),
        new Fixture("900106", "[개발용] 가평 가을 산 단풍 축제", "10-18", "10-19", "127.41867", "37.827", "경기도 가평군 (가짜 주소)"));

    private static final List<Fixture> ATTRACTIONS = List.of(
        new Fixture("910101", "[개발용] 내장산 단풍생태공원", "", "", "126.90267", "35.6204", "전북특별자치도 정읍시 (가짜 주소)"),
        new Fixture("910102", "[개발용] 단풍산", "", "", "128.45497", "37.217", "강원특별자치도 영월군 (가짜 주소)"),
        new Fixture("910103", "[개발용] 철암 단풍군락지", "", "", "128.9825", "37.13773", "강원특별자치도 태백시 (가짜 주소)"),
        new Fixture("910104", "[개발용] 속리산 단풍길", "", "", "", "", "충청북도 보은군 (가짜 주소)"),
        new Fixture("910105", "[개발용] 해인사 단풍 소리길", "", "", "128.09533", "35.53777", "경상남도 합천군 (가짜 주소)"),
        new Fixture("910201", "[개발용] 청풍 벚꽃길", "", "", "128.168", "37.1235", "충청북도 제천시 (가짜 주소)"));

    private DevTourApiFixtures() {}

    /** 키워드 검색(관광지) 응답 본문 — 이름에 키워드가 든 가짜 관광지. 키 모드는 행사정보조회와 같다. */
    public static String searchKeyword(String serviceKey, String keyword, int numOfRows, int pageNo) {
        if (KEY_REJECTED.equals(serviceKey)) return errorXml("SERVICE_KEY_IS_NOT_REGISTERED_ERROR", "30");
        if (KEY_QUOTA.equals(serviceKey)) return errorXml("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR", "22");
        if (KEY_BROKEN.equals(serviceKey)) return "{\"response\":";
        List<ObjectNode> matching = new ArrayList<>();
        for (Fixture fixture : ATTRACTIONS) {
            if (keyword != null && !keyword.isBlank() && fixture.title().contains(keyword)) matching.add(attraction(fixture));
        }
        matching.sort((left, right) -> left.get("title").asText().compareTo(right.get("title").asText()));
        return envelope(matching, numOfRows, pageNo);
    }

    private static ObjectNode attraction(Fixture fixture) {
        ObjectNode item = JSON.createObjectNode();
        item.put("addr1", fixture.address());
        item.put("addr2", "");
        item.put("zipcode", "");
        item.put("areacode", "");
        item.put("cat1", "A01");
        item.put("cat2", "A0101");
        item.put("cat3", "A01010500");
        item.put("contentid", fixture.contentId());
        item.put("contenttypeid", "12");
        item.put("createdtime", "20250101000000");
        item.put("firstimage", "");
        item.put("firstimage2", "");
        item.put("cpyrhtDivCd", "");
        item.put("mapx", fixture.mapx());
        item.put("mapy", fixture.mapy());
        item.put("mlevel", "6");
        item.put("modifiedtime", "20250101000000");
        item.put("sigungucode", "");
        item.put("tel", "");
        item.put("title", fixture.title());
        item.put("lDongRegnCd", "");
        item.put("lDongSignguCd", "");
        item.put("lclsSystm1", "NA");
        item.put("lclsSystm2", "NA04");
        item.put("lclsSystm3", "NA040500");
        return item;
    }

    /** 요청 변수 → 응답 본문(JSON 또는 오류 XML). */
    public static String searchFestival(String serviceKey, String eventStartDate, String eventEndDate, int numOfRows, int pageNo) {
        if (KEY_REJECTED.equals(serviceKey)) return errorXml("SERVICE_KEY_IS_NOT_REGISTERED_ERROR", "30");
        if (KEY_QUOTA.equals(serviceKey)) return errorXml("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR", "22");
        if (KEY_BROKEN.equals(serviceKey)) return "{\"response\":";
        LocalDate from = LocalDate.parse(eventStartDate, DATE);
        LocalDate until = eventEndDate == null || eventEndDate.isBlank() ? from.plusYears(1) : LocalDate.parse(eventEndDate, DATE);
        List<ObjectNode> matching = new ArrayList<>();
        for (int year = from.getYear(); year <= until.getYear(); year++) {
            for (Fixture fixture : FIXTURES) {
                LocalDate start = MonthDay.parse("--" + fixture.start()).atYear(year);
                LocalDate end = MonthDay.parse("--" + fixture.end()).atYear(year);
                if (!end.isBefore(from) && !start.isAfter(until)) matching.add(item(fixture, year, start, end));
            }
        }
        matching.sort((left, right) -> left.get("title").asText().compareTo(right.get("title").asText()));
        return envelope(matching, numOfRows, pageNo);
    }

    private static String envelope(List<ObjectNode> matching, int numOfRows, int pageNo) {
        int fromIndex = Math.min(matching.size(), (pageNo - 1) * numOfRows);
        int toIndex = Math.min(matching.size(), fromIndex + numOfRows);
        List<ObjectNode> page = matching.subList(fromIndex, toIndex);
        ObjectNode root = JSON.createObjectNode();
        ObjectNode response = root.putObject("response");
        ObjectNode header = response.putObject("header");
        header.put("resultCode", "0000");
        header.put("resultMsg", "OK");
        ObjectNode body = response.putObject("body");
        if (page.isEmpty()) {
            body.put("items", "");
        } else {
            ArrayNode items = body.putObject("items").putArray("item");
            page.forEach(items::add);
        }
        body.put("numOfRows", numOfRows);
        body.put("pageNo", pageNo);
        body.put("totalCount", matching.size());
        return root.toString();
    }

    private static ObjectNode item(Fixture fixture, int year, LocalDate start, LocalDate end) {
        ObjectNode item = JSON.createObjectNode();
        item.put("addr1", fixture.address());
        item.put("addr2", "");
        item.put("zipcode", "");
        item.put("cat1", "A02");
        item.put("cat2", "A0207");
        item.put("cat3", "A02070200");
        item.put("contentid", fixture.contentId() + (year % 100));
        item.put("contenttypeid", "15");
        item.put("createdtime", "20250101000000");
        item.put("eventstartdate", DATE.format(start));
        item.put("eventenddate", DATE.format(end));
        item.put("firstimage", "");
        item.put("firstimage2", "");
        item.put("cpyrhtDivCd", "");
        item.put("mapx", fixture.mapx());
        item.put("mapy", fixture.mapy());
        item.put("mlevel", "6");
        item.put("modifiedtime", "20250101000000");
        item.put("areacode", "");
        item.put("sigungucode", "");
        item.put("tel", "");
        item.put("title", fixture.title());
        item.put("lDongRegnCd", "");
        item.put("lDongSignguCd", "");
        item.put("lclsSystm1", "EV");
        item.put("lclsSystm2", "EV01");
        item.put("lclsSystm3", "EV010100");
        item.put("progresstype", "");
        item.put("festivaltype", "");
        return item;
    }

    private static String errorXml(String authMessage, String reasonCode) {
        return "<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg><returnAuthMsg>" + authMessage
            + "</returnAuthMsg><returnReasonCode>" + reasonCode + "</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>";
    }

    private record Fixture(String contentId, String title, String start, String end, String mapx, String mapy, String address) {}
}
