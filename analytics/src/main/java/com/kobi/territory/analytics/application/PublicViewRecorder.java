package com.kobi.territory.analytics.application;

import com.kobi.territory.analytics.domain.actor.Country;
import com.kobi.territory.analytics.domain.actor.DeviceType;
import com.kobi.territory.analytics.domain.ratelimit.ClientOrigin;
import com.kobi.territory.analytics.domain.tracking.EventDefinitions;
import com.kobi.territory.analytics.domain.tracking.PublicPage;
import com.kobi.territory.analytics.domain.tracking.TrackedEventRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 공개 페이지 열람 적기(요청 필터가 성공한 응답 뒤에 부른다). handle·주소·User-Agent 원문은 적지 않는다 — 어느 종류의 페이지인지,
 * 기기 유형(봇 포함)·나라만. 누가 봤는지는 모른다(사람 수 지표에는 안 들어가고 열람 수로만 센다).
 */
@Component
public class PublicViewRecorder {

    private final TrackedEventRepository events;
    private final AnalyticsSettings settings;
    private final EventDefinitions definitions = EventDefinitions.standard();
    private final Clock clock;

    public PublicViewRecorder(TrackedEventRepository events, AnalyticsSettings settings, Clock clock) {
        this.events = events;
        this.settings = settings;
        this.clock = clock;
    }

    /** @return 공개 페이지라 적었는지 */
    public boolean record(String path, String userAgent, ClientOrigin origin) {
        DeviceType device = DeviceType.classify(userAgent);
        Country country = Country.fromHeader(settings.trustedProxies().countryHeader(origin)).orElse(null);
        return PublicPage.classify(path)
            .map(view -> definitions.require(view.eventName())
                .request(view.fields(), device, country, clock.instant(), settings.ingestPolicy()))
            .map(event -> {
                events.appendAll(List.of(event));
                return true;
            })
            .orElse(false);
    }
}
