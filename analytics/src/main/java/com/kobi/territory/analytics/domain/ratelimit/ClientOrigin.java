package com.kobi.territory.analytics.domain.ratelimit;

/**
 * 요청이 어디서 왔는지 — 판단 재료만(저장하지 않는다).
 *
 * @param socketAddress   실제로 접속한 주소(TCP 상대 — 프록시 뒤면 그 프록시)
 * @param cloudflareIp    {@code CF-Connecting-IP} 헤더(없으면 null) — 신뢰하는 프록시를 거친 요청일 때만 믿는다
 * @param forwardedFor    {@code X-Forwarded-For} 헤더 원문(없으면 null) — 신뢰하는 프록시가 붙인 오른쪽 구간만 믿는다
 * @param countryHeader   {@code CF-IPCountry} 헤더(없으면 null) — 신뢰하는 프록시를 거친 요청일 때만 믿는다
 */
public record ClientOrigin(String socketAddress, String cloudflareIp, String forwardedFor, String countryHeader) {

    /** 프록시 헤더 없이 접속 주소만. */
    public static ClientOrigin direct(String socketAddress) {
        return new ClientOrigin(socketAddress, null, null, null);
    }
}
