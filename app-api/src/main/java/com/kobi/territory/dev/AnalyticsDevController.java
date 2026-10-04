package com.kobi.territory.dev;

import com.kobi.territory.analytics.application.AnalyticsSeedService;
import com.kobi.territory.analytics.application.MetricsBatchJob;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 분석(10단계) local 전용 — DevController 와 같은 조건(local 프로파일 + territory.dev.enabled=true)일 때만 생긴다.
 * <ul>
 *   <li>{@code POST /dev/analytics/seed?days=30&visitorsPerDay=40&seed=42} — 분석 테이블을 비우고 가짜 방문·가입·체크인·재방문·초대·
 *       오류·카드 열람을 지난 days 일치 만든 뒤 일 배치를 돌린다(지표 화면 확인용). 게임 데이터는 건드리지 않는다. 200 {seeded, batch}</li>
 *   <li>{@code POST /dev/analytics/batch} — 일 배치를 지금 돌린다(운영 POST /admin/metrics/batch 와 같다). 200 {from, to, days, purged, elapsedMs}</li>
 * </ul>
 */
@Profile("local")
@ConditionalOnProperty(prefix = "territory.dev", name = "enabled", havingValue = "true")
@RestController
@RequestMapping("/dev/analytics")
public class AnalyticsDevController {

    /** 시드 전에 비우는 분석 테이블(FK 없음). */
    static final List<String> ANALYTICS_TABLES = List.of("analytics_event", "analytics_visitor", "analytics_explorer",
        "analytics_daily_breakdown", "analytics_daily", "analytics_cohort");

    private final AnalyticsSeedService seeds;
    private final MetricsBatchJob batch;
    private final JdbcTemplate jdbc;

    public AnalyticsDevController(AnalyticsSeedService seeds, MetricsBatchJob batch, JdbcTemplate jdbc) {
        this.seeds = seeds;
        this.batch = batch;
        this.jdbc = jdbc;
    }

    @PostMapping("/seed")
    public Map<String, Object> seed(@RequestParam(name = "days", defaultValue = "30") int days,
                                    @RequestParam(name = "visitorsPerDay", defaultValue = "40") int visitorsPerDay,
                                    @RequestParam(name = "seed", defaultValue = "42") long seed) {
        if (days < 1 || days > 90 || visitorsPerDay < 1 || visitorsPerDay > 2000) {
            throw new IllegalArgumentException("days 1~90, visitorsPerDay 1~2000");
        }
        ANALYTICS_TABLES.forEach(table -> jdbc.update("DELETE FROM " + table));
        AnalyticsSeedService.SeedResult seeded = seeds.seed(days, visitorsPerDay, seed);
        return Map.of("seeded", seeded, "batch", batch.run());
    }

    @PostMapping("/batch")
    public MetricsBatchJob.BatchResult batch() {
        return batch.run();
    }
}
