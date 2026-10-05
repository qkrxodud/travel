package com.kobi.territory.dev;

import com.kobi.territory.catalog.application.SeasonLineupService;
import com.kobi.territory.catalog.domain.lineup.CollectionPlan;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가짜 TourAPI(13s단계) local 전용 — DevController 와 같은 조건(local 프로파일 + territory.dev.enabled=true)일 때만 생긴다. 실제 키 없이 키 있는 흐름을
 * 확인한다: {@code TOURAPI_SERVICE_KEY=아무값 --territory.tourapi.base-url=http://localhost:포트/dev/tourapi} 로 띄우면 서버가 자기 자신의
 * {@code GET /dev/tourapi/searchFestival2} 를 부른다(응답은 {@link DevTourApiFixtures}).
 * <ul>
 *   <li>{@code GET /dev/tourapi/searchKeyword2} — 키워드 검색(관광지) 가짜 응답</li>
 *   <li>{@code PUT /dev/tourapi/mode {"mode": "ok" | "no-attractions" | "key-rejected" | "quota" | "broken"}} — 가짜 서버의 다음 응답들(관광지 0건·
 *       키 거절 XML 30·한도 초과 XML 22·깨진 본문). 200 {mode}</li>
 *   <li>{@code POST /dev/tourapi/collect} — 자동 수집을 지금 한 번(스케줄·기동 직후와 같은 판단). 200 {회차: 계획}</li>
 * </ul>
 */
@Profile("local")
@ConditionalOnProperty(prefix = "territory.dev", name = "enabled", havingValue = "true")
@RestController
@RequestMapping("/dev/tourapi")
public class TourApiDevController {

    private static final Map<String, String> MODES = Map.of("key-rejected", DevTourApiFixtures.KEY_REJECTED, "quota",
        DevTourApiFixtures.KEY_QUOTA, "broken", DevTourApiFixtures.KEY_BROKEN);

    private final SeasonLineupService lineups;
    private final AtomicReference<String> mode = new AtomicReference<>("ok");

    public TourApiDevController(SeasonLineupService lineups) {
        this.lineups = lineups;
    }

    @GetMapping("/searchFestival2")
    public ResponseEntity<String> searchFestival(@RequestParam("serviceKey") String serviceKey,
                                                 @RequestParam("eventStartDate") String eventStartDate,
                                                 @RequestParam(value = "eventEndDate", required = false) String eventEndDate,
                                                 @RequestParam(value = "numOfRows", defaultValue = "10") int numOfRows,
                                                 @RequestParam(value = "pageNo", defaultValue = "1") int pageNo) {
        String effectiveKey = MODES.getOrDefault(mode.get(), serviceKey);
        String body = DevTourApiFixtures.searchFestival(effectiveKey, eventStartDate, eventEndDate, numOfRows, pageNo);
        MediaType type = body.startsWith("<") ? MediaType.TEXT_XML : MediaType.APPLICATION_JSON;
        return ResponseEntity.ok().contentType(new MediaType(type, StandardCharsets.UTF_8)).body(body);
    }

    @GetMapping("/searchKeyword2")
    public ResponseEntity<String> searchKeyword(@RequestParam("serviceKey") String serviceKey,
                                                @RequestParam("keyword") String keyword,
                                                @RequestParam(value = "numOfRows", defaultValue = "10") int numOfRows,
                                                @RequestParam(value = "pageNo", defaultValue = "1") int pageNo) {
        String effectiveKey = MODES.getOrDefault(mode.get(), serviceKey);
        String body = "no-attractions".equals(mode.get()) ? DevTourApiFixtures.searchKeyword(serviceKey, "", numOfRows, pageNo)
            : DevTourApiFixtures.searchKeyword(effectiveKey, keyword, numOfRows, pageNo);
        MediaType type = body.startsWith("<") ? MediaType.TEXT_XML : MediaType.APPLICATION_JSON;
        return ResponseEntity.ok().contentType(new MediaType(type, StandardCharsets.UTF_8)).body(body);
    }

    @PutMapping("/mode")
    public ResponseEntity<Map<String, String>> mode(@RequestBody Map<String, String> body) {
        String requested = body.getOrDefault("mode", "ok");
        if (!requested.equals("ok") && !requested.equals("no-attractions") && !MODES.containsKey(requested)) {
            return ResponseEntity.badRequest().body(Map.of("message", "mode 는 ok | no-attractions | key-rejected | quota | broken"));
        }
        mode.set(requested);
        return ResponseEntity.ok(Map.of("mode", requested));
    }

    @PostMapping("/collect")
    public Map<String, CollectionPlan> collect() {
        return lineups.collectAutomatically();
    }
}
