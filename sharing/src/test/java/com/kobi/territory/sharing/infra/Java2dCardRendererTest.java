package com.kobi.territory.sharing.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.sharing.domain.card.CardKind;
import com.kobi.territory.sharing.domain.showcase.CardComposer;
import com.kobi.territory.sharing.domain.showcase.CardContent;
import com.kobi.territory.sharing.domain.showcase.ProvinceInfo;
import com.kobi.territory.sharing.domain.showcase.PublicVisits;
import com.kobi.territory.sharing.domain.showcase.RegionAtlas;
import com.kobi.territory.sharing.domain.showcase.RegionInfo;
import com.kobi.territory.sharing.domain.showcase.Showcase;
import com.kobi.territory.sharing.domain.showcase.ShowcaseProgress;
import com.kobi.territory.sharing.domain.showcase.ShowcaseScene;
import com.kobi.territory.sharing.domain.showcase.VisitFact;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import javax.imageio.ImageIO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 회귀 출처: 4단계 OG 카드 렌더러(OFL 글꼴 번들).
 * 렌더 결과 스냅샷(픽셀 단위 비교 대신 구조 확인): 1200×630 PNG, 번들 한글 글꼴 사용, 지도 영역에 "내 영토" 색 픽셀, 오른쪽 글자
 * 영역에 흰 글자 픽셀, 아래 공개 경로 막대. 실패 분석용으로 build/card-snapshots 에 PNG 를 남긴다.
 */
@DisplayName("카드 그림")
class Java2dCardRendererTest {

    static Java2dCardRenderer renderer;
    static Showcase showcase;

    @BeforeAll
    static void setUp() throws IOException {
        try (InputStream geo = Java2dCardRendererTest.class.getClassLoader().getResourceAsStream("catalog/regions.geojson")) {
            renderer = Java2dCardRenderer.of(new String(geo.readAllBytes(), StandardCharsets.UTF_8));
        }
        RegionAtlas atlas = RegionAtlas.of(List.of(
            new RegionInfo("KR-11010", "종로구", "KR-11", "서울", Rarity.COMMON),
            new RegionInfo("KR-39010", "제주시", "KR-39", "제주", Rarity.COMMON),
            new RegionInfo("KR-37430", "울릉군", "KR-37", "경북", Rarity.LEGEND)),
            List.of(new ProvinceInfo("KR-11", "서울", 25), new ProvinceInfo("KR-39", "제주", 2), new ProvinceInfo("KR-37", "경북", 22)));
        Instant at = Instant.parse("2026-10-03T03:00:00Z");
        PublicVisits visits = PublicVisits.of(List.of(new VisitFact("KR-11010", LocalDate.parse("2026-10-01"), at),
            new VisitFact("KR-39010", LocalDate.parse("2026-09-12"), at.plusSeconds(1)),
            new VisitFact("KR-37430", LocalDate.parse("2025-07-12"), at.plusSeconds(2))), atlas);
        showcase = new Showcase("kim", atlas, visits, new ShowcaseProgress(4, "전국 방방곡곡 섬 수집가", 2, 1, 9),
            new ShowcaseScene(7, List.of("청사초롱 등불", "제주 감귤 모자"), 3));
    }

    /** 카드 4종을 그려 build/card-snapshots 에 남긴다(실패 분석용). */
    static Map<String, BufferedImage> renderAll() throws IOException {
        Showcase other = new Showcase("lee", showcase.atlas(), PublicVisits.of(List.of(
            new VisitFact("KR-11010", LocalDate.parse("2026-01-01"), Instant.EPOCH)), showcase.atlas()),
            ShowcaseProgress.start(9), ShowcaseScene.empty());
        List<CardContent> contents = List.of(
            CardComposer.compose(CardKind.TERRITORY, showcase, Year.of(2026)),
            CardComposer.compose(CardKind.RECENT, showcase, Year.of(2026)),
            CardComposer.compose(CardKind.RECAP, showcase, Year.of(2026)),
            CardComposer.versus(showcase, other));
        Path out = Path.of("build", "card-snapshots");
        Files.createDirectories(out);
        Map<String, BufferedImage> images = new LinkedHashMap<>();
        for (CardContent content : contents) {
            byte[] png = renderer.render(content);
            assertThat(png).startsWith(0x89, 'P', 'N', 'G');
            String name = content.getClass().getSimpleName().toLowerCase();
            Files.write(out.resolve(name + ".png"), png);
            images.put(name, ImageIO.read(new ByteArrayInputStream(png)));
        }
        return images;
    }

    @Nested
    @DisplayName("글꼴")
    class Fonts {

        @Test
        @DisplayName("함께 넣은 글꼴로 그린다")
        void bundled() {
            assertThat(renderer.fonts().source()).startsWith("bundled");
        }

        @Test
        @DisplayName("한글을 그릴 수 있다")
        void displaysKorean() {
            assertThat(renderer.fonts().displaysKorean()).isTrue();
        }

        @Test
        @DisplayName("제목 글꼴에 없는 가운뎃점도 빈 네모 없이 그린다")
        void fallsBackForMissingGlyphs() {
            String line = "Lv.4 · 스트릭 2개월";

            assertThat(renderer.fonts().covering(renderer.fonts().display(34), line).canDisplayUpTo(line)).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("카드 네 종류 모두")
    class AllKinds {

        @Test
        @DisplayName("링크 미리보기 크기(1200×630) 그림이다")
        void ogSize() throws IOException {
            renderAll().forEach((name, image) -> {
                assertThat(image.getWidth()).as(name).isEqualTo(1200);
                assertThat(image.getHeight()).as(name).isEqualTo(630);
            });
        }

        @Test
        @DisplayName("왼쪽에 우리나라 지도 실루엣을 그린다")
        void mapSilhouette() throws IOException {
            renderAll().forEach((name, image) ->
                assertThat(countPixels(image, 40, 40, 560, 590, Java2dCardRenderer.EMPTY_REGION)).as(name).isGreaterThan(20_000));
        }

        @Test
        @DisplayName("오른쪽에 흰 글자를 쓴다")
        void whiteText() throws IOException {
            renderAll().forEach((name, image) ->
                assertThat(countNear(image, 620, 50, 1180, 540, Color.WHITE)).as(name).isGreaterThan(300));
        }

        @Test
        @DisplayName("아래에 공개 주소 막대를 그린다")
        void footerBar() throws IOException {
            renderAll().forEach((name, image) ->
                assertThat(countPixels(image, 640, 548, 644, 582, Java2dCardRenderer.MINE)).as(name).isGreaterThan(100));
        }
    }

    @Test
    @DisplayName("영토 카드에는 칠한 지역이 내 색으로 보인다")
    void paintedRegions() throws IOException {
        assertThat(countPixels(renderAll().get("territory"), 40, 40, 560, 590, Java2dCardRenderer.MINE)).isGreaterThan(30);
    }

    private static int countPixels(BufferedImage image, int left, int top, int right, int bottom, Color color) {
        int count = 0;
        for (int pixelY = top; pixelY < bottom; pixelY++) {
            for (int pixelX = left; pixelX < right; pixelX++) {
                if ((image.getRGB(pixelX, pixelY) & 0xffffff) == (color.getRGB() & 0xffffff)) count++;
            }
        }
        return count;
    }

    private static int countNear(BufferedImage image, int left, int top, int right, int bottom, Color color) {
        int count = 0;
        for (int pixelY = top; pixelY < bottom; pixelY++) {
            for (int pixelX = left; pixelX < right; pixelX++) {
                Color pixel = new Color(image.getRGB(pixelX, pixelY));
                if (Math.abs(pixel.getRed() - color.getRed()) < 20 && Math.abs(pixel.getGreen() - color.getGreen()) < 20
                    && Math.abs(pixel.getBlue() - color.getBlue()) < 20) count++;
            }
        }
        return count;
    }
}
