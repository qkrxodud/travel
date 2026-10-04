package com.kobi.territory.analytics.api.web;

import com.kobi.territory.analytics.api.web.AnalyticsDtos.EventsRequest;
import com.kobi.territory.analytics.api.web.AnalyticsDtos.EventsResponse;
import com.kobi.territory.analytics.application.ClientEventService;
import com.kobi.territory.analytics.application.ClientEvents;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 화면 이벤트 수집 {@code POST /events}(10단계). 인증 없이도 받고, 로그인 세션·X-Explorer-Token 이 있으면 그 탐험가(해시로만)와 잇는다
 * (모르는 토큰은 401 대신 익명). 본문은 JSON 만 — 다른 사이트의 폼이 보낼 수 없는 형식이라 CSRF 검사에서 뺐다(SecurityConfig).
 * 본문 크기는 {@link EventsBodyLimitFilter}, 레이트 리밋·묶음 크기·이벤트 검증은 서비스·도메인이 맡는다.
 */
@RestController
public class EventController {

    private final ClientEventService events;

    public EventController(ClientEventService events) {
        this.events = events;
    }

    @PostMapping(path = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventsResponse collect(@RequestBody EventsRequest request, @CurrentExplorer(required = false) ExplorerId explorer,
                                  HttpServletRequest http) {
        return EventsResponse.from(events.ingest(new ClientEvents(request.visitorId(), explorer == null ? null : explorer.value(),
            request.submitted(), http.getHeader(HttpHeaders.USER_AGENT), RequestOrigin.of(http))));
    }
}
