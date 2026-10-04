package com.kobi.territory.analytics.domain.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 믿는 프록시 목록(설정 territory.analytics.trusted-proxies — CIDR 또는 주소, 기본 비어 있음 = 아무도 믿지 않음).
 * 레이트 리밋의 "요청 주소"를 정한다:
 * <ol>
 *   <li>실제 접속 주소가 믿는 프록시가 아니면 그 주소(클라이언트가 보낸 X-Forwarded-For·CF-Connecting-IP 는 무시 — 위조 가능)</li>
 *   <li>믿는 프록시를 거쳤으면 {@code CF-Connecting-IP}(Cloudflare 가 정한 방문자 주소)</li>
 *   <li>그 헤더가 없으면 {@code X-Forwarded-For} 를 오른쪽부터 보며 믿는 프록시를 건너뛴 첫 주소(왼쪽 값은 클라이언트가 마음대로 쓸 수 있다)</li>
 * </ol>
 * 나라 헤더({@code CF-IPCountry})도 믿는 프록시를 거친 요청일 때만 쓴다. IP 리터럴만 다룬다(이름 조회 없음).
 */
public final class TrustedProxies {

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*");

    private final List<AddressRange> ranges;

    private TrustedProxies(List<AddressRange> ranges) {
        this.ranges = ranges;
    }

    public static TrustedProxies none() {
        return new TrustedProxies(List.of());
    }

    /** "10.0.0.0/8", "172.18.0.5", "::1/128" 처럼. 형식이 틀리면 시작하지 않는다. */
    public static TrustedProxies of(List<String> entries) {
        return new TrustedProxies(entries.stream().map(String::trim).filter(entry -> !entry.isEmpty()).map(AddressRange::parse).toList());
    }

    public boolean trusts(String address) {
        return literal(address).map(bytes -> ranges.stream().anyMatch(range -> range.contains(bytes))).orElse(false);
    }

    /** 레이트 리밋에 쓸 요청 주소. */
    public String clientAddress(ClientOrigin origin) {
        String socket = origin.socketAddress();
        if (!trusts(socket)) return socket;
        if (origin.cloudflareIp() != null && literal(origin.cloudflareIp().trim()).isPresent()) return origin.cloudflareIp().trim();
        if (origin.forwardedFor() == null) return socket;
        List<String> hops = Arrays.stream(origin.forwardedFor().split(",")).map(String::trim).filter(hop -> !hop.isEmpty()).toList();
        for (int i = hops.size() - 1; i >= 0; i--) {
            if (!trusts(hops.get(i))) return hops.get(i);
        }
        return socket;
    }

    /**
     * 앞단 프록시가 붙어 있는데 믿는 프록시 설정이 비었거나 맞지 않아 보이는지(10단계 QA r2 P3-d) — 실제 접속 주소가 사설·루프백 주소(같은
     * 호스트·compose 네트워크의 프록시로 보임)이고 믿지 않는 주소인데 프록시 헤더(CF-Connecting-IP·X-Forwarded-For)가 왔다. 이대로면 모든 방문자가
     * 프록시 주소 하나로 세져 주소 버킷 하나를 나눠 쓰고, 사용자가 늘면 수집이 조용히 429 로 버려진다.
     */
    public boolean ignoresForwardingFrom(ClientOrigin origin) {
        if (origin.cloudflareIp() == null && origin.forwardedFor() == null) return false;
        if (trusts(origin.socketAddress())) return false;
        return literal(origin.socketAddress()).map(TrustedProxies::privateOrLoopback).orElse(false);
    }

    private static boolean privateOrLoopback(byte[] address) {
        try {
            InetAddress parsed = InetAddress.getByAddress(address);
            if (parsed.isLoopbackAddress() || parsed.isSiteLocalAddress() || parsed.isLinkLocalAddress()) return true;
            return address.length == 16 && (address[0] & 0xFE) == 0xFC;  // IPv6 고유 로컬(fc00::/7)
        } catch (UnknownHostException impossible) {
            return false;
        }
    }

    /** 믿는 프록시를 거친 요청이면 나라 헤더, 아니면 없음. */
    public String countryHeader(ClientOrigin origin) {
        return trusts(origin.socketAddress()) ? origin.countryHeader() : null;
    }

    static Optional<byte[]> literal(String address) {
        if (address == null || !(IPV4.matcher(address).matches() || IPV6.matcher(address).matches())) return Optional.empty();
        try {
            return Optional.of(InetAddress.getByName(address).getAddress());
        } catch (UnknownHostException | SecurityException invalid) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "TrustedProxies[" + ranges.size() + "개 범위]";
    }

    /** 주소 범위 하나(CIDR). */
    private static final class AddressRange {
        private final byte[] network;
        private final int prefix;

        private AddressRange(byte[] network, int prefix) {
            this.network = network;
            this.prefix = prefix;
        }

        static AddressRange parse(String entry) {
            String[] parts = entry.split("/", 2);
            byte[] network = literal(parts[0]).orElseThrow(() -> new IllegalArgumentException("믿는 프록시 주소 형식이 틀림: " + entry));
            int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : network.length * 8;
            if (prefix < 0 || prefix > network.length * 8) throw new IllegalArgumentException("CIDR 접두 길이가 틀림: " + entry);
            return new AddressRange(network, prefix);
        }

        boolean contains(byte[] address) {
            if (address.length != network.length) return false;
            int whole = prefix / 8;
            for (int i = 0; i < whole; i++) {
                if (address[i] != network[i]) return false;
            }
            int rest = prefix % 8;
            if (rest == 0) return true;
            int mask = 0xFF << (8 - rest) & 0xFF;
            return (address[whole] & mask) == (network[whole] & mask);
        }
    }
}
