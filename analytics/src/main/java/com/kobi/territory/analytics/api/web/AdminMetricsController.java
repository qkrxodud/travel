package com.kobi.territory.analytics.api.web;

import com.kobi.territory.analytics.api.web.AnalyticsDtos.BatchResponse;
import com.kobi.territory.analytics.api.web.AnalyticsDtos.MetricsResponse;
import com.kobi.territory.analytics.application.AnalyticsSettings;
import com.kobi.territory.analytics.application.MetricsBatchJob;
import com.kobi.territory.analytics.application.MetricsQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 지표(10단계) — /admin/** 라 X-Admin-Token 필수(없으면 401 ADMIN_TOKEN_REQUIRED, 틀리면 403 ADMIN_TOKEN_INVALID).
 * <ul>
 *   <li>{@code GET /admin/metrics?days=30} — 지난 날(일 배치 값) + 오늘(실시간). days 1~90</li>
 *   <li>{@code POST /admin/metrics/batch} — 일 배치를 지금 돌린다(다시 계산 + 보관 기간 지난 원본 삭제)</li>
 * </ul>
 */
@RestController
public class AdminMetricsController {

    private final MetricsQueryService metrics;
    private final MetricsBatchJob batch;
    private final AnalyticsSettings settings;

    public AdminMetricsController(MetricsQueryService metrics, MetricsBatchJob batch, AnalyticsSettings settings) {
        this.metrics = metrics;
        this.batch = batch;
        this.settings = settings;
    }

    @GetMapping("/admin/metrics")
    public MetricsResponse metrics(@RequestParam(name = "days", defaultValue = "30") int days) {
        return MetricsResponse.from(metrics.report(days), settings.ingestPolicy().zone().getId(),
            settings.metricsPolicy().topErrorCodes());
    }

    @PostMapping("/admin/metrics/batch")
    public BatchResponse runBatch() {
        return BatchResponse.from(batch.run());
    }
}
