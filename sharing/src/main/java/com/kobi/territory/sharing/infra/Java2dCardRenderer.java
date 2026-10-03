package com.kobi.territory.sharing.infra;

import com.kobi.territory.catalog.api.query.RegionCatalog;
import com.kobi.territory.sharing.domain.showcase.CardContent;
import com.kobi.territory.sharing.domain.showcase.CardRenderer;
import com.kobi.territory.sharing.domain.showcase.MapPaint;
import com.kobi.territory.sharing.domain.showcase.Tone;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 자랑 카드 PNG 렌더러(Java2D headless) — 프로토타입 카드 4종(territoryCard·recentCard·recapCard·vsCard)의 1200×630 배치를
 * 옮겼다: 왼쪽 지도 실루엣(칠한 지역), 오른쪽 큰 숫자·문구·집계, 아래 공개 프로필 경로. 장면은 그림 대신 입은 아이템 이름 상자로
 * 요약한다(이모지는 서버 글꼴로 그릴 수 없다). 글꼴은 {@link CardFonts}.
 */
@Component
class Java2dCardRenderer implements CardRenderer {

    static final int WIDTH = 1200;
    static final int HEIGHT = 630;
    static final int SCENE_BOX_LEFT = 904;
    static final Rectangle2D MAP_EXTENT = new Rectangle2D.Double(50, 50, 510, 530);

    private static final Logger log = LoggerFactory.getLogger(Java2dCardRenderer.class);

    static final Color BACKGROUND = new Color(0x10211f);
    static final Color GRID = new Color(255, 255, 255, 13);
    static final Color EMPTY_REGION = new Color(0x1f3330);
    static final Color MINE = new Color(0x2fc3ad);
    static final Color HIGHLIGHT = new Color(0xffd166);
    static final Color FADED = new Color(0x2a4a45);
    static final Color BOTH = new Color(0xb48af0);
    static final Color LEGEND_RING = new Color(0xd7b3ff);
    static final Color LABEL = new Color(0x8fb3ad);
    static final Color SUBTLE = new Color(0xcfe6e1);
    static final Color FOOTER = new Color(0x5f8f87);

    static {
        if (System.getProperty("java.awt.headless") == null) System.setProperty("java.awt.headless", "true");
    }

    private final CardFonts fonts;
    private final KoreaSilhouette silhouette;

    /** 스프링 빈 생성자(생성자 주입 — 테스트용 생성자가 따로 있어 지정한다). */
    @Autowired
    Java2dCardRenderer(RegionCatalog regions) {
        this.fonts = CardFonts.load();
        this.silhouette = KoreaSilhouette.fit(regions.regionsGeoJson(), MAP_EXTENT);
        log.info("카드 렌더러 준비: 글꼴 {}, 지역 {}곳", fonts.source(), silhouette.paths().size());
    }

    /** 테스트용 — GeoJSON 원문으로 만든다. */
    static Java2dCardRenderer of(String regionsGeoJson) {
        return new Java2dCardRenderer(CardFonts.load(), KoreaSilhouette.fit(regionsGeoJson, MAP_EXTENT));
    }

    private Java2dCardRenderer(CardFonts fonts, KoreaSilhouette silhouette) {
        this.fonts = fonts;
        this.silhouette = silhouette;
    }

    CardFonts fonts() {
        return fonts;
    }

    @Override
    public byte[] render(CardContent content) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            background(graphics);
            map(graphics, content.paint());
            switch (content) {
                case CardContent.Territory territory -> territory(graphics, territory);
                case CardContent.Recent recent -> recent(graphics, recent);
                case CardContent.Recap recap -> recap(graphics, recap);
                case CardContent.Versus versus -> versus(graphics, versus);
            }
            footer(graphics, content.footer());
        } finally {
            graphics.dispose();
        }
        return png(image);
    }

    // ---- 카드별 오른쪽 ------------------------------------------------------------------------------------------

    private void territory(Graphics2D graphics, CardContent.Territory card) {
        // 오른쪽 "착용 중" 상자(x=904~)와 겹치지 않게 머리글 폭을 상자 앞까지로(QA P3-3) — 넘치면 줄이고, 그래도 넘치면 말줄임
        text(graphics, card.headline(), 640, 70, fonts.bold(22), LABEL, SCENE_BOX_LEFT - 640 - 14);
        text(graphics, card.conquestPercent() + "%", 634, 100, fonts.display(100), Color.WHITE, 260);
        text(graphics, card.total() + "곳 중 " + card.visited() + "곳", 640, 215, fonts.bold(22), SUBTLE, 260);
        sceneBox(graphics, card.wornItemNames());
        stats(graphics, card.stats(), 268, 50, 820);
    }

    private void recent(Graphics2D graphics, CardContent.Recent card) {
        text(graphics, card.headline(), 640, 70, fonts.bold(22), LABEL, 520);
        if (card.regionName() != null) {
            text(graphics, card.regionName(), 634, 105, fonts.display(96), card.legend() ? LEGEND_RING : Color.WHITE, 520);
        }
        text(graphics, card.detail(), 640, 220, fonts.bold(28), SUBTLE, 520);
        stats(graphics, card.stats(), 470, 48, 820);
    }

    private void recap(Graphics2D graphics, CardContent.Recap card) {
        text(graphics, card.headline(), 640, 70, fonts.bold(22), LABEL, 520);
        text(graphics, card.newRegions() + "곳", 634, 100, fonts.display(110), Color.WHITE, 520);
        text(graphics, card.subline(), 640, 235, fonts.bold(26), SUBTLE, 520);
        List<Integer> months = card.monthCounts();
        int max = Math.max(1, months.stream().mapToInt(Integer::intValue).max().orElse(0));
        for (int i = 0; i < months.size(); i++) {
            int barX = 640 + i * 44;
            double barHeight = 90.0 * months.get(i) / max;
            graphics.setColor(months.get(i) > 0 ? MINE : EMPTY_REGION);
            graphics.fill(new Rectangle2D.Double(barX, 400 - barHeight, 32, Math.max(4, barHeight)));
            text(graphics, String.valueOf(i + 1), barX + 10, 408, fonts.body(14), FOOTER, 40);
        }
        stats(graphics, card.stats(), 450, 44, 860);
    }

    private void versus(Graphics2D graphics, CardContent.Versus card) {
        text(graphics, "영토 전쟁", 640, 70, fonts.bold(22), LABEL, 520);
        text(graphics, String.valueOf(card.tally().mine()), 634, 105, fonts.display(84), MINE, 160);
        text(graphics, "vs", 800, 130, fonts.display(40), FOOTER, 70);
        text(graphics, String.valueOf(card.tally().theirs()), 880, 105, fonts.display(84), HIGHLIGHT, 260);
        text(graphics, card.myName(), 640, 200, fonts.body(24), SUBTLE, 230);
        text(graphics, card.theirName(), 880, 200, fonts.body(24), SUBTLE, 270);
        List<VersusRow> rows = List.of(new VersusRow("나만 간 곳", card.tally().onlyMine(), MINE),
            new VersusRow("둘 다 간 곳", card.tally().both(), BOTH),
            new VersusRow(card.theirName() + "만 간 곳", card.tally().onlyTheirs(), HIGHLIGHT));
        for (int i = 0; i < rows.size(); i++) {
            int rowY = 280 + i * 58;
            VersusRow row = rows.get(i);
            graphics.setColor(row.color());
            graphics.fill(new Rectangle2D.Double(640, rowY + 8, 14, 14));
            text(graphics, row.label(), 668, rowY, fonts.body(22), LABEL, 220);
            text(graphics, row.count() + "곳", 900, rowY - 6, fonts.display(34), Color.WHITE, 250);
        }
    }

    private record VersusRow(String label, int count, Color color) {}

    // ---- 공통 --------------------------------------------------------------------------------------------------

    private void background(Graphics2D graphics) {
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setColor(GRID);
        graphics.setStroke(new BasicStroke(1f));
        for (int i = 0; i < WIDTH; i += 40) graphics.drawLine(i, 0, i, HEIGHT);
        for (int i = 0; i < HEIGHT; i += 40) graphics.drawLine(0, i, WIDTH, i);
    }

    private void map(Graphics2D graphics, MapPaint paint) {
        graphics.setStroke(new BasicStroke(0.8f));
        for (Map.Entry<String, Path2D> region : silhouette.paths().entrySet()) {
            graphics.setColor(paint.toneOf(region.getKey()).map(Java2dCardRenderer::colorOf).orElse(EMPTY_REGION));
            graphics.fill(region.getValue());
            graphics.setColor(BACKGROUND);
            graphics.draw(region.getValue());
        }
        graphics.setStroke(new BasicStroke(2.5f));
        graphics.setColor(LEGEND_RING);
        silhouette.paths().keySet().stream().filter(paint::ringed).forEach(code -> silhouette.center(code)
            .ifPresent(center -> graphics.draw(new Ellipse2D.Double(center.getX() - 7, center.getY() - 7, 14, 14))));
        paint.highlighted().ifPresent(code -> {
            Path2D path = silhouette.paths().get(code);
            if (path == null) return;
            graphics.setColor(HIGHLIGHT);
            graphics.setStroke(new BasicStroke(3f));
            graphics.draw(path);
            silhouette.center(code).ifPresent(center -> {
                graphics.setStroke(new BasicStroke(2f));
                graphics.draw(new Ellipse2D.Double(center.getX() - 16, center.getY() - 16, 32, 32));
            });
        });
    }

    private void sceneBox(Graphics2D graphics, List<String> wornItemNames) {
        RoundRectangle2D box = new RoundRectangle2D.Double(SCENE_BOX_LEFT, 50, 248, 155, 16, 16);
        graphics.setColor(new Color(0x16302c));
        graphics.fill(box);
        graphics.setColor(MINE);
        graphics.setStroke(new BasicStroke(2f));
        graphics.draw(box);
        text(graphics, "착용 중", 920, 62, fonts.bold(16), LABEL, 220);
        List<String> shown = wornItemNames.isEmpty() ? List.of("아직 입은 아이템이 없어요") : wornItemNames;
        for (int i = 0; i < Math.min(4, shown.size()); i++) {
            String line = i == 3 && shown.size() > 4 ? "외 " + (shown.size() - 3) + "개" : shown.get(i);
            text(graphics, line, 920, 90 + i * 27, fonts.body(18), Color.WHITE, 220);
        }
    }

    private void stats(Graphics2D graphics, List<CardContent.Stat> stats, int top, int step, int valueX) {
        for (int i = 0; i < stats.size(); i++) {
            int rowY = top + i * step;
            text(graphics, stats.get(i).label(), 640, rowY, fonts.body(20), LABEL, valueX - 650);
            text(graphics, stats.get(i).value(), valueX, rowY - 6, fonts.display(34), Color.WHITE, WIDTH - valueX - 40);
        }
    }

    private void footer(Graphics2D graphics, String footer) {
        graphics.setColor(MINE);
        graphics.fillRect(640, 548, 4, 34);
        text(graphics, footer, 656, 556, fonts.body(20), FOOTER, 500);
    }

    /**
     * 위쪽 기준(top)으로 글을 쓴다. 글꼴에 없는 글자(예: Do Hyeon 의 '·')가 있으면 본문 글꼴로 바꾸고, 너비를 넘으면 글꼴을
     * 줄이고(최소 60%), 그래도 넘치면 끝을 "…"로 줄인다.
     */
    private void text(Graphics2D graphics, String value, int left, int top, Font font, Color color, int maxWidth) {
        Font fitted = fonts.covering(font, value);
        FontMetrics metrics = graphics.getFontMetrics(fitted);
        while (metrics.stringWidth(value) > maxWidth && fitted.getSize2D() > font.getSize2D() * 0.6f) {
            fitted = fitted.deriveFont(fitted.getSize2D() - 2f);
            metrics = graphics.getFontMetrics(fitted);
        }
        String shown = value;
        while (metrics.stringWidth(shown) > maxWidth && shown.length() > 1) {
            shown = shown.substring(0, shown.length() - (shown.endsWith("…") ? 2 : 1)) + "…";
        }
        graphics.setFont(fitted);
        graphics.setColor(color);
        graphics.drawString(shown, left, top + metrics.getAscent());
    }

    private static Color colorOf(Tone tone) {
        return switch (tone) {
            case MINE -> MINE;
            case HIGHLIGHT, THEIRS -> HIGHLIGHT;
            case FADED -> FADED;
            case BOTH -> BOTH;
        };
    }

    private static byte[] png(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException("카드 PNG 인코딩 실패", failure);
        }
    }
}
