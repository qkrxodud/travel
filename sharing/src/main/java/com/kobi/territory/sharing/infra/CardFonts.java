package com.kobi.territory.sharing.infra;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 카드 글꼴 — 한글이 깨지지 않게 리소스에 OFL 글꼴을 넣어 쓴다(sharing/src/main/resources/fonts, 라이선스 OFL-*.txt 동봉):
 * 제목 Do Hyeon(프로토타입 디스플레이 글꼴), 본문 Nanum Gothic Regular·Bold. 리소스를 못 읽으면 한글을 그릴 수 있는 시스템 글꼴
 * (Noto Sans KR·Apple SD Gothic Neo·Malgun Gothic 등)로, 그것도 없으면 논리 글꼴 SansSerif 로 내려가며 WARN 을 남긴다
 * (그때는 서버에 한글 글꼴 패키지를 설치해야 한다 — 예: fonts-nanum).
 */
final class CardFonts {

    private static final Logger log = LoggerFactory.getLogger(CardFonts.class);
    static final String KOREAN_SAMPLE = "나의 영토 가나다 서울 제주";
    private static final List<String> SYSTEM_FALLBACKS = List.of("Noto Sans KR", "Noto Sans CJK KR", "NanumGothic",
        "Nanum Gothic", "Apple SD Gothic Neo", "AppleGothic", "Malgun Gothic", "UnDotum", "Baekmuk Gulim");

    private final Font display;
    private final Font body;
    private final Font bodyBold;
    private final String source;

    private CardFonts(Font display, Font body, Font bodyBold, String source) {
        this.display = display;
        this.body = body;
        this.bodyBold = bodyBold;
        this.source = source;
    }

    static CardFonts load() {
        Optional<Font> display = bundled("fonts/DoHyeon-Regular.ttf");
        Optional<Font> body = bundled("fonts/NanumGothic-Regular.ttf");
        Optional<Font> bold = bundled("fonts/NanumGothic-Bold.ttf");
        if (display.isPresent() && body.isPresent() && bold.isPresent()) {
            return new CardFonts(display.get(), body.get(), bold.get(), "bundled(DoHyeon, NanumGothic)");
        }
        Font system = systemKoreanFont().orElse(null);
        if (system != null) {
            log.warn("카드 글꼴 리소스를 읽지 못해 시스템 글꼴 {} 로 그립니다.", system.getFamily());
            return new CardFonts(display.orElse(system), body.orElse(system), bold.orElse(system.deriveFont(Font.BOLD)),
                "system(" + system.getFamily() + ")");
        }
        log.warn("한글을 그릴 수 있는 글꼴이 없습니다 — 카드의 한글이 깨질 수 있습니다(서버에 한글 글꼴을 설치하세요).");
        Font logical = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        return new CardFonts(logical, logical, logical.deriveFont(Font.BOLD), "logical(SansSerif)");
    }

    private static Optional<Font> bundled(String resource) {
        try (InputStream in = CardFonts.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) return Optional.empty();
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            return font.canDisplayUpTo(KOREAN_SAMPLE) == -1 ? Optional.of(font) : Optional.empty();
        } catch (IOException | FontFormatException unreadable) {
            log.warn("카드 글꼴 {} 를 읽지 못했습니다: {}", resource, unreadable.toString());
            return Optional.empty();
        }
    }

    private static Optional<Font> systemKoreanFont() {
        List<String> installed = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        return SYSTEM_FALLBACKS.stream().filter(installed::contains).map(family -> new Font(family, Font.PLAIN, 12))
            .filter(font -> font.canDisplayUpTo(KOREAN_SAMPLE) == -1).findFirst();
    }

    Font display(float size) {
        return display.deriveFont(Font.PLAIN, size);
    }

    Font body(float size) {
        return body.deriveFont(Font.PLAIN, size);
    }

    Font bold(float size) {
        return bodyBold.deriveFont(Font.PLAIN, size);
    }

    /** preferred 가 text 의 글자를 모두 그릴 수 있으면 그대로, 아니면 같은 크기의 본문 글꼴(한글·문장부호를 다 가진다). */
    Font covering(Font preferred, String text) {
        if (preferred.canDisplayUpTo(text) == -1) return preferred;
        Font fallback = body.deriveFont(Font.PLAIN, preferred.getSize2D());
        return fallback.canDisplayUpTo(text) == -1 ? fallback : preferred;
    }

    /** 어떤 글꼴로 그리는지(로그·테스트). */
    String source() {
        return source;
    }

    boolean displaysKorean() {
        return display.canDisplayUpTo(KOREAN_SAMPLE) == -1 && body.canDisplayUpTo(KOREAN_SAMPLE) == -1;
    }
}
