package com.kobi.territory.catalog.domain.mystery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.RegionCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 미스터리 주차 시드: 서버 비밀값과 주차로 정해지는 수 — 같으면 같고, 비밀값 없이는 만들 수 없으며, 비밀값은 드러나지 않는다. */
@DisplayName("미스터리 주차 시드")
class MysterySeedTest {

    private static final LocalDate 월요일 = LocalDate.of(2026, 10, 5);

    @Test
    @DisplayName("같은 비밀값·같은 주면 다시 띄워도 같은 수다")
    void deterministic() {
        assertThat(new MysterySeed("salt").of(월요일)).isEqualTo(new MysterySeed("salt").of(월요일));
        assertThat(new MysterySeed("salt").of(월요일, RegionCode.of("KR-37430")))
            .isEqualTo(new MysterySeed("salt").of(월요일, RegionCode.of("KR-37430")));
    }

    @Test
    @DisplayName("주가 바뀌거나 비밀값이 바뀌면 다른 수다")
    void differs() {
        assertThat(new MysterySeed("salt").of(월요일.plusWeeks(1))).isNotEqualTo(new MysterySeed("salt").of(월요일));
        assertThat(new MysterySeed("other").of(월요일)).isNotEqualTo(new MysterySeed("salt").of(월요일));
    }

    @Test
    @DisplayName("비밀값이 없으면 시드를 만들 수 없다")
    void secretRequired() {
        assertThatThrownBy(() -> new MysterySeed(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MysterySeed(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀값은 글자로 드러나지 않는다")
    void hidden() {
        assertThat(new MysterySeed("top-secret").toString()).doesNotContain("top-secret");
    }
}
