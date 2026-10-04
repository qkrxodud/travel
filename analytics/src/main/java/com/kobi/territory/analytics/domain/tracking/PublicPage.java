package com.kobi.territory.analytics.domain.tracking;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공개 페이지 열람 — 공개 프로필 {@code /u/{handle}}, 자랑 카드 {@code /u/{handle}/card/{kind}.png}, VS 카드 {@code /u/{a}/vs/{b}.png}.
 * handle 은 적지 않는다(어느 카드 종류인지만 — 경로의 대소문자는 가리지 않고 소문자로). 성공한(200) 열람만 센다 — 판단은 요청 필터가 한다.
 */
public enum PublicPage {
    PROFILE(EventDefinitions.PROFILE_VIEW, Pattern.compile("/u/[^/]+/?")),
    CARD(EventDefinitions.CARD_VIEW, Pattern.compile("/u/[^/]+/card/([A-Za-z0-9_-]{1,32})\\.png")),
    COMPARE_CARD(EventDefinitions.COMPARE_CARD_VIEW, Pattern.compile("/u/[^/]+/vs/[^/]+\\.png"));

    private final String eventName;
    private final Pattern path;

    PublicPage(String eventName, Pattern path) {
        this.eventName = eventName;
        this.path = path;
    }

    /** 경로가 공개 페이지면 그 이벤트(이름 + 필드). */
    public static Optional<PageView> classify(String requestPath) {
        if (requestPath == null) return Optional.empty();
        for (PublicPage page : values()) {
            Matcher matcher = page.path.matcher(requestPath);
            if (matcher.matches()) {
                Map<String, Object> fields = page == CARD ? Map.of("kind", matcher.group(1).toLowerCase(Locale.ROOT)) : Map.of();
                return Optional.of(new PageView(page.eventName, fields));
            }
        }
        return Optional.empty();
    }

    /** 공개 페이지 열람 한 번(이벤트 이름 + 필드). */
    public record PageView(String eventName, Map<String, Object> fields) {
        public PageView {
            fields = Map.copyOf(fields);
        }
    }
}
