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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 렌더 결과 스냅샷(픽셀 단위 비교 대신 구조 확인): 1200×630 PNG, 번들 한글 글꼴 사용, 지도 영역에 "내 영토" 색 픽셀, 오른쪽 글자
 * 영역에 흰 글자 픽셀, 아래 공개 경로 막대. 실패 분석용으로 build/card-snapshots 에 PNG 를 남긴다.
 */
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

    @Test
    void 번들_한글_글꼴로_그린다() {
        assertThat(renderer.fonts().source()).startsWith("bundled");
        assertThat(renderer.fonts().displaysKorean()).isTrue();
        // 제목 글꼴(Do Hyeon)에 없는 '·' 는 본문 글꼴로 내려가 두부(□)가 생기지 않는다
        String line = "Lv.4 · 스트릭 2개월";
        assertThat(renderer.fonts().covering(renderer.fonts().display(34), line).canDisplayUpTo(line)).isEqualTo(-1);
    }

    @Test
    void 카드_4종이_1200x630_PNG_이고_지도와_글자_영역이_그려진다() throws IOException {
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
        for (CardContent content : contents) {
            byte[] png = renderer.render(content);
            assertThat(png).startsWith(0x89, 'P', 'N', 'G');
            String name = content.getClass().getSimpleName().toLowerCase();
            Files.write(out.resolve(name + ".png"), png);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(image.getWidth()).isEqualTo(1200);
            assertThat(image.getHeight()).isEqualTo(630);
            assertThat(countPixels(image, 40, 40, 560, 590, Java2dCardRenderer.EMPTY_REGION))
                .as(name + " 지도 실루엣(빈 지역)").isGreaterThan(20_000);
            assertThat(countNear(image, 620, 50, 1180, 540, Color.WHITE)).as(name + " 오른쪽 흰 글자").isGreaterThan(300);
            assertThat(countPixels(image, 640, 548, 644, 582, Java2dCardRenderer.MINE)).as(name + " 아래 막대").isGreaterThan(100);
        }
        BufferedImage territory = ImageIO.read(new ByteArrayInputStream(renderer.render(contents.get(0))));
        assertThat(countPixels(territory, 40, 40, 560, 590, Java2dCardRenderer.MINE)).as("칠한 지역").isGreaterThan(30);
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
