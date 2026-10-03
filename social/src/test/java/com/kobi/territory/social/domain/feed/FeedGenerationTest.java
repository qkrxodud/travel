package com.kobi.territory.social.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 회귀 출처: 5단계 리더 결정 4(소식 다시 쌓기 — 다음 세대에 쌓은 뒤 바꾼다). */
@DisplayName("소식 다시 쌓기 세대")
class FeedGenerationTest {

    @Test
    @DisplayName("다시 쌓을 때는 지금 다음 세대에 쌓는다")
    void next() {
        assertThat(new FeedGeneration(1).next()).isEqualTo(new FeedGeneration(2));
    }

    @Test
    @DisplayName("세대는 1부터 센다")
    void startsAtOne() {
        assertThatThrownBy(() -> new FeedGeneration(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
