package com.kobi.territory.analytics.domain.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("공개 페이지 열람")
class PublicPageTest {

    @Test
    @DisplayName("공개 프로필을 열면 프로필 열람이다")
    void profile() {
        assertThat(PublicPage.classify("/u/hong-gildong")).hasValueSatisfying(view -> {
            assertThat(view.eventName()).isEqualTo("profile_view");
            assertThat(view.fields()).isEmpty();
        });
    }

    @Test
    @DisplayName("자랑 카드 이미지는 카드 종류만(소문자로) 적고 handle 은 적지 않는다")
    void card() {
        assertThat(PublicPage.classify("/u/hong-gildong/card/TERRITORY.png")).hasValueSatisfying(view -> {
            assertThat(view.eventName()).isEqualTo("card_view");
            assertThat(view.fields()).isEqualTo(Map.of("kind", "territory"));
            assertThat(view.toString()).doesNotContain("hong-gildong");
        });
    }

    @Test
    @DisplayName("VS 카드 이미지는 VS 카드 열람이다")
    void compare() {
        assertThat(PublicPage.classify("/u/a/vs/b.png")).map(PublicPage.PageView::eventName).hasValue("compare_card_view");
    }

    @ParameterizedTest(name = "{0} 은 공개 페이지가 아니다")
    @ValueSource(strings = {"/u/", "/u/a/card/.png", "/me/cards/map.png", "/u/a/b/c"})
    @DisplayName("다른 경로는 공개 페이지 열람이 아니다")
    void others(String path) {
        assertThat(PublicPage.classify(path)).isEmpty();
    }
}
