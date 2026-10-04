package com.kobi.territory.notification.domain.recipient;

import com.kobi.territory.notification.domain.NotificationError;
import com.kobi.territory.notification.domain.push.PushEndpoint;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 받을 구독 주소의 규칙 — 서버가 구독 주소로 직접 요청을 보내므로(SSRF) 알려진 푸시 서비스 호스트만 받는다(설정
 * territory.push.allowed-hosts: {@code fcm.googleapis.com} 처럼 정확히, {@code *.push.apple.com} 처럼 하위 도메인). https 만.
 * local 은 {@code allowLocalhost} 로 localhost·127.0.0.1 을 http 로도 받는다(개발용 가짜 푸시 서비스).
 */
public record EndpointRules(List<String> allowedHosts, boolean allowLocalhost) {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    public EndpointRules {
        allowedHosts = allowedHosts.stream().map(host -> host.trim().toLowerCase(Locale.ROOT)).filter(host -> !host.isEmpty()).toList();
        if (allowedHosts.stream().anyMatch(host -> host.equals("*") || host.startsWith("*") && !host.startsWith("*."))) {
            throw new IllegalArgumentException("allowed-hosts 는 호스트 또는 *.도메인");
        }
    }

    /** 받을 수 없는 주소면 PUSH_ENDPOINT_NOT_ALLOWED. */
    public void require(PushEndpoint endpoint) {
        if (!allows(endpoint)) throw NotificationError.PUSH_ENDPOINT_NOT_ALLOWED.exception();
    }

    public boolean allows(PushEndpoint endpoint) {
        String host = endpoint.host();
        if (allowLocalhost && LOCAL_HOSTS.contains(host)) return true;
        return endpoint.secure() && allowedHosts.stream().anyMatch(allowed -> matches(allowed, host));
    }

    private static boolean matches(String allowed, String host) {
        if (allowed.startsWith("*.")) return host.endsWith(allowed.substring(1)) && host.length() > allowed.length() - 1;
        return host.equals(allowed);
    }
}
