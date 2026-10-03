package com.kobi.territory.exploration.domain.territory;

import static com.kobi.territory.exploration.domain.Fixtures.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.exploration.domain.ExplorationError;
import com.kobi.territory.exploration.domain.ExplorationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("방문 기록의 값")
class VisitRecordValuesTest {

    @Nested
    @DisplayName("메모")
    class MemoRules {

        @Test
        @DisplayName("40자까지 쓸 수 있다")
        void fortyChars() {
            assertThat(Memo.of("가".repeat(40)).value()).hasSize(40);
        }

        @Test
        @DisplayName("41자는 입력 오류로 거절된다")
        void fortyOneRefused() {
            ExplorationException refused = catchThrowableOfType(ExplorationException.class, () -> Memo.of("가".repeat(41)));
            assertThat(refused.error()).isEqualTo(ExplorationError.MEMO_TOO_LONG);
            assertThat(refused.kind()).isEqualTo(ErrorKind.INVALID);
        }

        @Test
        @DisplayName("이모지도 한 글자로 센다")
        void emojiIsOneChar() {
            String emoji40 = "🍜".repeat(40);
            assertThat(Memo.of(emoji40).value()).isEqualTo(emoji40);
        }

        @Test
        @DisplayName("비우거나 공백만 쓰면 빈 메모다")
        void blankIsEmpty() {
            assertThat(Memo.of(null)).isEqualTo(Memo.EMPTY);
            assertThat(Memo.of("   ").isEmpty()).isTrue();
        }

        @Test
        @DisplayName("앞뒤 공백은 잘라서 글자 수를 센다")
        void trimmed() {
            assertThat(Memo.of("  물회 ").value()).isEqualTo("물회");
            assertThat(Memo.of(" " + "가".repeat(40)).value()).hasSize(40);
        }
    }

    @Test
    @DisplayName("방문일은 꼭 있어야 한다")
    void visitDateRequired() {
        assertThat(refusal(() -> VisitDate.of(null))).isEqualTo(ExplorationError.INVALID_VISIT_DATE);
    }

    @Test
    @DisplayName("사진 주소를 비우면 사진이 없는 것이다")
    void blankPhotoIsNone() {
        assertThat(PhotoRef.ofNullable(" ")).isNull();
        assertThat(PhotoRef.ofNullable("https://x/y.jpg").url()).isEqualTo("https://x/y.jpg");
    }
}
