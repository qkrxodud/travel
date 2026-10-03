package com.kobi.territory.exploration.domain.map;

import static com.kobi.territory.exploration.domain.Fixtures.CODE;
import static com.kobi.territory.exploration.domain.Fixtures.MAP;
import static com.kobi.territory.exploration.domain.Fixtures.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.ExplorationException;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("지도의 값")
class MapValuesTest {

    @Nested
    @DisplayName("초대코드")
    class Invite {

        @Test
        @DisplayName("새 코드는 8자이고 헷갈리는 0·O·1·I를 쓰지 않는다")
        void generated() {
            assertThat(InviteCode.generate(new Random(1)).value()).hasSize(8).doesNotContain("0", "O", "1", "I");
        }

        @Test
        @DisplayName("8자가 아니거나 쓰지 않는 글자가 있으면 코드가 아니다")
        void invalid() {
            assertThatThrownBy(() -> new InviteCode("ABC")).isInstanceOf(ExplorationException.class);
            assertThatThrownBy(() -> new InviteCode("ABCDEFG0")).isInstanceOf(ExplorationException.class);
        }

        @Test
        @DisplayName("입력한 코드는 공백을 자르고 대문자로 읽는다")
        void parsesInput() {
            assertThat(InviteCode.parse(" abcdefgh ")).contains(CODE);
        }

        @Test
        @DisplayName("잘못 입력한 코드는 없는 코드로 읽는다")
        void parsesInvalidAsNone() {
            assertThat(InviteCode.parse("ABC")).isEmpty();
            assertThat(InviteCode.parse("ABCDEFG0")).isEmpty();
        }
    }

    @Nested
    @DisplayName("지도 설정")
    class Settings {

        @Test
        @DisplayName("하루 상한은 한 곳 이상이어야 한다")
        void capAtLeastOne() {
            assertThatThrownBy(() -> new MapSettings(false, 0, MapVisibility.PRIVATE)).isInstanceOf(ExplorationException.class);
        }

        @Test
        @DisplayName("공개 범위를 고르지 않으면 비공개다")
        void defaultPrivate() {
            assertThat(MapVisibility.parseOrPrivate(null)).isEqualTo(MapVisibility.PRIVATE);
        }

        @Test
        @DisplayName("공개 범위는 대소문자 없이 읽는다")
        void parsesVisibility() {
            assertThat(MapVisibility.parseOrPrivate("friends")).isEqualTo(MapVisibility.FRIENDS);
        }
    }

    @Nested
    @DisplayName("지도 고르기")
    class Selector {

        @Test
        @DisplayName("지도를 고르지 않으면 개인 지도다")
        void omittedIsPersonal() {
            assertThat(MapSelector.of((String) null).personal()).isTrue();
            assertThat(MapSelector.of(" ")).isEqualTo(MapSelector.PERSONAL);
        }

        @Test
        @DisplayName("지도를 고르면 그 지도다")
        void explicit() {
            MapSelector selector = MapSelector.of(MAP.value());
            assertThat(selector.personal()).isFalse();
            assertThat(selector.explicit()).contains(MAP);
        }

        @Test
        @DisplayName("고른 지도가 없으면 지도를 찾을 수 없다고 알리고 개인 지도면 그렇게 말한다")
        void notFound() {
            assertThat(MapSelector.of(MAP.value()).notFound().error()).isEqualTo(ExplorationError.MAP_NOT_FOUND);
            assertThat(MapSelector.PERSONAL.notFound().getMessage()).contains("개인 지도");
        }

        @Test
        @DisplayName("형식이 틀린 지도 번호는 없는 지도다")
        void badMapId() {
            assertThat(refusal(() -> MapId.of("x"))).isEqualTo(ExplorationError.MAP_NOT_FOUND);
        }
    }
}
