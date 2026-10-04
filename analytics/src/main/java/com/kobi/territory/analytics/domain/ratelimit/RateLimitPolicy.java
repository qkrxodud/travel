package com.kobi.territory.analytics.domain.ratelimit;

/**
 * 화면 이벤트 수집의 레이트 리밋(설정 territory.analytics.ingest.rate-limit.*). 요청 수 기준 토큰 버킷 두 겹:
 * 익명 방문 ID 마다, 그리고 요청 주소마다(방문 ID 를 바꿔 가며 보내는 경우 — 주소는 메모리에서만 쓰고 저장하지 않는다).
 *
 * @param visitorBurst     방문 하나가 한꺼번에 보낼 수 있는 요청 수
 * @param visitorPerMinute 방문 하나에 1분마다 채워지는 요청 수
 * @param addressBurst     주소 하나가 한꺼번에 보낼 수 있는 요청 수
 * @param addressPerMinute 주소 하나에 1분마다 채워지는 요청 수
 * @param maxTrackedKeys   기억하는 버킷 수 상한(넘으면 가득 찬 버킷부터 잊는다 — 메모리 보호)
 */
public record RateLimitPolicy(int visitorBurst, int visitorPerMinute, int addressBurst, int addressPerMinute, int maxTrackedKeys) {

    public RateLimitPolicy {
        if (visitorBurst < 1 || visitorPerMinute < 1 || addressBurst < 1 || addressPerMinute < 1 || maxTrackedKeys < 1) {
            throw new IllegalArgumentException("레이트 리밋 값은 1 이상");
        }
    }
}
