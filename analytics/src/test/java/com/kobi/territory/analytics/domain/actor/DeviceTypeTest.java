package com.kobi.territory.analytics.domain.actor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("기기 유형")
class DeviceTypeTest {

    @ParameterizedTest(name = "{1} ← {0}")
    @CsvSource(delimiter = '|', value = {
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile/15E148 | MOBILE",
        "Mozilla/5.0 (Linux; Android 14; SM-S918N) Chrome/120 Mobile Safari/537.36 | MOBILE",
        "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) | TABLET",
        "Mozilla/5.0 (Linux; Android 13; SM-X710) Chrome/120 Safari/537.36 | TABLET",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Safari/605.1.15 | DESKTOP",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120 | DESKTOP",
        "facebookexternalhit/1.1;kakaotalk-scrap/1.0 | BOT",
        "Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots) | BOT",
        "Mozilla/5.0 (compatible; Googlebot/2.1) | BOT",
        "curl/8.4.0 | BOT",
        "Mozilla/5.0 (X11; Linux x86_64) HeadlessChrome/120 | BOT",
        "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36 DaumApps/8.0.0 DaumDevice/mobile | MOBILE",
        "Mozilla/5.0 (compatible; Daum/4.1; +http://cs.daum.net/faq/15/4118.html) Daumoa/4.0 | BOT"
    })
    @DisplayName("User-Agent 는 휴대폰·태블릿·컴퓨터·봇으로만 거칠게 나눈다 — 다음 앱 안의 브라우저는 사람이다")
    void classify(String userAgent, DeviceType expected) {
        assertThat(DeviceType.classify(userAgent)).isEqualTo(expected);
    }

    @Test
    @DisplayName("User-Agent 가 없으면 모름이고, 모름은 사람으로 센다")
    void unknown() {
        assertThat(DeviceType.classify(null)).isEqualTo(DeviceType.UNKNOWN);
        assertThat(DeviceType.UNKNOWN.human()).isTrue();
        assertThat(DeviceType.BOT.human()).isFalse();
    }
}
