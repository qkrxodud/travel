package com.kobi.territory.analytics.domain.actor;

import java.util.List;
import java.util.Locale;

/**
 * 기기 유형 — User-Agent 원문은 저장하지 않고 이 다섯 갈래로만 거칠게 나눈다. 봇은 사람 지표(DAU·퍼널 등)에서 뺀다.
 * 분류는 일부러 단순하다(정확한 기기 판별이 목적이 아니라 "휴대폰이 대부분인가"를 보는 정도).
 */
public enum DeviceType {
    MOBILE, TABLET, DESKTOP, BOT, UNKNOWN;

    /** 링크 미리보기·검색 크롤러·명령줄 도구 등 사람이 아닌 요청의 흔한 표식. */
    /**
     * 사람 브라우저와 겹치지 않을 만큼 좁힌 표식(QA P3-3): 다음 앱 인앱 브라우저(DaumApps)는 사람이라 "daum" 이 아니라 다음 검색 로봇
     * "daumoa" 만, 링크 미리보기는 일반 "preview" 대신 서비스별 이름(카카오톡·슬랙·스카이프 등)으로.
     */
    private static final List<String> BOT_MARKS = List.of("bot", "crawler", "spider", "slurp", "facebookexternalhit",
        "kakaotalk-scrap", "daumoa", "yeti", "headless", "curl/", "wget", "python-requests", "httpclient", "java/", "okhttp",
        "go-http-client", "scrapy", "embedly", "whatsapp", "discord", "telegram", "skypeuripreview", "vkshare", "pinterest");

    public static DeviceType classify(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return UNKNOWN;
        String agent = userAgent.toLowerCase(Locale.ROOT);
        if (BOT_MARKS.stream().anyMatch(agent::contains)) return BOT;
        if (agent.contains("ipad") || agent.contains("tablet") || (agent.contains("android") && !agent.contains("mobile"))) {
            return TABLET;
        }
        if (agent.contains("mobi") || agent.contains("iphone") || agent.contains("android")) return MOBILE;
        if (agent.contains("windows") || agent.contains("macintosh") || agent.contains("x11") || agent.contains("cros")) {
            return DESKTOP;
        }
        return UNKNOWN;
    }

    public boolean human() {
        return this != BOT;
    }
}
