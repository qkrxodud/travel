package com.kobi.territory.analytics.domain.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("요청 주소 — 믿는 프록시")
class TrustedProxiesTest {

    private static final TrustedProxies TUNNEL = TrustedProxies.of(List.of("172.16.0.0/12", "127.0.0.1"));

    @Nested
    @DisplayName("믿는 프록시가 없을 때(기본)")
    class Nobody {

        @Test
        @DisplayName("클라이언트가 X-Forwarded-For·CF-Connecting-IP 를 바꿔 보내도 실제 접속 주소로 센다")
        void spoofingIgnored() {
            ClientOrigin spoofed = new ClientOrigin("203.0.113.9", "198.51.100.1", "198.51.100.2, 198.51.100.3", "US");

            assertThat(TrustedProxies.none().clientAddress(spoofed)).isEqualTo("203.0.113.9");
            assertThat(TrustedProxies.none().countryHeader(spoofed)).isNull();
        }
    }

    @Nested
    @DisplayName("믿는 프록시(터널)를 거쳐 왔을 때")
    class ViaTunnel {

        @Test
        @DisplayName("Cloudflare 가 정한 방문자 주소로 센다")
        void cloudflareIp() {
            assertThat(TUNNEL.clientAddress(new ClientOrigin("172.18.0.5", "198.51.100.7", "1.2.3.4", "KR"))).isEqualTo("198.51.100.7");
            assertThat(TUNNEL.countryHeader(new ClientOrigin("172.18.0.5", null, null, "KR"))).isEqualTo("KR");
        }

        @Test
        @DisplayName("Cloudflare 주소가 없으면 X-Forwarded-For 를 오른쪽부터 보며 믿는 프록시를 건너뛴 첫 주소 — 왼쪽에 끼워 넣은 값은 무시된다")
        void rightmostUntrusted() {
            ClientOrigin origin = new ClientOrigin("172.18.0.5", null, "6.6.6.6, 198.51.100.7, 172.18.0.9", null);

            assertThat(TUNNEL.clientAddress(origin)).isEqualTo("198.51.100.7");
        }

        @Test
        @DisplayName("헤더가 없거나 모두 믿는 프록시면 접속 주소로 센다")
        void fallback() {
            assertThat(TUNNEL.clientAddress(ClientOrigin.direct("172.18.0.5"))).isEqualTo("172.18.0.5");
            assertThat(TUNNEL.clientAddress(new ClientOrigin("172.18.0.5", "not-an-ip", "172.18.0.9", null))).isEqualTo("172.18.0.5");
        }
    }

    @Nested
    @DisplayName("설정")
    class Settings {

        @Test
        @DisplayName("주소 범위(CIDR)로 믿는 프록시를 정하고, IPv6 도 다룬다")
        void ranges() {
            TrustedProxies proxies = TrustedProxies.of(List.of("10.0.0.0/8", "::1", " "));

            assertThat(proxies.trusts("10.200.3.4")).isTrue();
            assertThat(proxies.trusts("11.0.0.1")).isFalse();
            assertThat(proxies.trusts("::1")).isTrue();
            assertThat(proxies.trusts("example.com")).isFalse();
            assertThat(TrustedProxies.of(List.of("172.16.0.0/12")).trusts("172.31.255.255")).isTrue();
            assertThat(TrustedProxies.of(List.of("172.16.0.0/12")).trusts("172.32.0.1")).isFalse();
        }

        @Test
        @DisplayName("형식이 틀린 범위면 시작하지 않는다")
        void invalid() {
            assertThatThrownBy(() -> TrustedProxies.of(List.of("cloudflared"))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TrustedProxies.of(List.of("10.0.0.0/40"))).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
