package com.kobi.territory.analytics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kobi.territory.support.IntegrationTestConfig;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 10단계 QA P2-2: 레이트 리밋의 요청 주소. 운영과 같게 forward-headers-strategy=framework 로 띄우고(X-Forwarded-For 맨 왼쪽을
 * getRemoteAddr 로 내주는 감싼 요청), 믿는 프록시는 127.0.0.1 하나. 주소 버킷은 몰아서 3번, 다시 차는 속도는 거의 0.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:territory-client-address;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "server.forward-headers-strategy=framework",
    "territory.analytics.trusted-proxies=127.0.0.1/32",
    "territory.analytics.ingest.rate-limit.address-burst=3",
    "territory.analytics.ingest.rate-limit.address-per-minute=1"
})
@AutoConfigureMockMvc
@Import(IntegrationTestConfig.class)
@DisplayName("이벤트 수집의 요청 주소")
class AnalyticsClientAddressTest {

    @Autowired MockMvc mvc;

    private MockHttpServletRequestBuilder 보내기(String socketAddress) {
        String body = "{\"visitorId\":\"visitor-" + UUID.randomUUID() + "\",\"events\":[{\"name\":\"checkin_open\"}]}";
        return post("/events").contentType(MediaType.APPLICATION_JSON).content(body)
            .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (iPhone) Mobile")
            .with(request -> {
                request.setRemoteAddr(socketAddress);
                return request;
            });
    }

    private ResultActions 보낸다(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request);
    }

    @Nested
    @DisplayName("믿지 않는 곳에서 바로 접속했을 때")
    class Direct {

        @Test
        @DisplayName("방문 ID 와 X-Forwarded-For·CF-Connecting-IP 를 매번 바꿔도 주소 상한에서 막힌다")
        void spoofingDoesNotBypass() throws Exception {
            for (int i = 0; i < 3; i++) {
                보낸다(보내기("203.0.113.9").header("X-Forwarded-For", "198.51.100." + i).header("CF-Connecting-IP", "192.0.2." + i))
                    .andExpect(status().isAccepted());
            }
            보낸다(보내기("203.0.113.9").header("X-Forwarded-For", "198.51.100.99").header("CF-Connecting-IP", "192.0.2.99"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("EVENTS_RATE_LIMITED"));
        }
    }

    @Nested
    @DisplayName("믿는 프록시(터널)를 거쳐 왔을 때")
    class ViaTrustedProxy {

        @Test
        @DisplayName("Cloudflare 가 알려 준 방문자 주소마다 따로 센다")
        void perCloudflareClient() throws Exception {
            for (int i = 0; i < 3; i++) 보낸다(보내기("127.0.0.1").header("CF-Connecting-IP", "198.51.100.10")).andExpect(status().isAccepted());

            보낸다(보내기("127.0.0.1").header("CF-Connecting-IP", "198.51.100.10")).andExpect(status().isTooManyRequests());
            보낸다(보내기("127.0.0.1").header("CF-Connecting-IP", "198.51.100.11")).andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("X-Forwarded-For 왼쪽에 아무 주소나 끼워 넣어도 프록시가 붙인 주소로 센다")
        void leftmostIgnored() throws Exception {
            for (int i = 0; i < 3; i++) {
                보낸다(보내기("127.0.0.1").header("X-Forwarded-For", "6.6.6." + i + ", 198.51.100.20")).andExpect(status().isAccepted());
            }
            보낸다(보내기("127.0.0.1").header("X-Forwarded-For", "6.6.6.99, 198.51.100.20")).andExpect(status().isTooManyRequests());
        }
    }
}
