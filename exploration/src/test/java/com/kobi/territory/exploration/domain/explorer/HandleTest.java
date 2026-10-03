package com.kobi.territory.exploration.domain.explorer;

import static com.kobi.territory.exploration.domain.Fixtures.refusal;
import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.exploration.domain.ExplorationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 공개 주소에 쓰는 핸들. 회귀 출처: 4단계 사용자 결정 Q1. */
@DisplayName("핸들")
class HandleTest {

    @Nested
    @DisplayName("형식")
    class Format {

        @Test
        @DisplayName("앞의 @와 공백을 떼고 소문자로 읽는다")
        void normalized() {
            assertThat(Handle.of(" @Kim_01 ").value()).isEqualTo("kim_01");
        }

        @Test
        @DisplayName("20자까지 쓸 수 있다")
        void twenty() {
            assertThat(Handle.of("a".repeat(20)).value()).hasSize(20);
        }

        @Test
        @DisplayName("중간의 하이픈은 쓸 수 있다")
        void hyphen() {
            assertThat(Handle.of("kim-lee").value()).isEqualTo("kim-lee");
        }

        @ParameterizedTest(name = "\"{0}\"은 핸들로 쓸 수 없다")
        @ValueSource(strings = {"ab", "_kim", "-kim", "kim.lee", "kim lee", "aaaaaaaaaaaaaaaaaaaaa"})
        @DisplayName("3~20자, 첫 글자 영숫자, 소문자·숫자·밑줄·하이픈이 아니면 쓸 수 없다")
        void invalid(String raw) {
            assertThat(refusal(() -> Handle.of(raw))).isEqualTo(ExplorationError.HANDLE_INVALID);
        }

        @Test
        @DisplayName("비워 둘 수 없다")
        void empty() {
            assertThat(refusal(() -> Handle.of(null))).isEqualTo(ExplorationError.HANDLE_INVALID);
        }
    }

    @Nested
    @DisplayName("금칙어")
    class Reserved {

        @Test
        @DisplayName("관리용 이름은 쓸 수 없다")
        void refused() {
            assertThat(refusal(() -> Handle.of("Admin"))).isEqualTo(ExplorationError.HANDLE_RESERVED);
        }

        @Test
        @DisplayName("주소로 찾을 때 금칙어와 잘못된 형식은 없는 핸들로 다룬다")
        void lookupTreatsAsMissing() {
            assertThat(Handle.parse("admin")).isEmpty();
            assertThat(Handle.parse("bad handle!")).isEmpty();
            assertThat(Handle.parse("@KIM")).contains(new Handle("kim"));
        }
    }

    @Nested
    @DisplayName("처음 로그인할 때 자동으로 받는 핸들")
    class Random {

        @Test
        @DisplayName("이메일과 무관한 explorer-네 글자다")
        void unrelatedToEmail() {
            Handle handle = Handle.random(new java.util.Random(7), candidate -> false);
            assertThat(handle.value()).matches("explorer-[a-z2-9]{4}").doesNotContain("kim");
        }

        @Test
        @DisplayName("같은 난수열이면 같은 핸들이다 — 이메일을 입력으로 쓰지 않는다")
        void deterministic() {
            assertThat(Handle.random(new java.util.Random(7), candidate -> false))
                .isEqualTo(Handle.random(new java.util.Random(7), candidate -> false));
        }

        @Test
        @DisplayName("겹치면 다시 뽑고 다섯 번 겹치면 여섯 글자로 늘린다")
        void growsAfterCollisions() {
            List<String> seen = new ArrayList<>();
            Handle handle = Handle.random(new java.util.Random(1), candidate -> {
                seen.add(candidate.value());
                return seen.size() <= 5;
            });
            assertThat(seen).hasSize(6);
            assertThat(seen.subList(0, 5)).allSatisfy(value -> assertThat(value).matches("explorer-[a-z2-9]{4}"));
            assertThat(handle.value()).matches("explorer-[a-z2-9]{6}");
        }
    }
}
