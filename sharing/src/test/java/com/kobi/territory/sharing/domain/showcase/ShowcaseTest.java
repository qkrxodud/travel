package com.kobi.territory.sharing.domain.showcase;

import static com.kobi.territory.sharing.domain.Fixtures.JONGNO;
import static com.kobi.territory.sharing.domain.Fixtures.showcase;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("공개 요약")
class ShowcaseTest {

    @Nested
    @DisplayName("공개 이름이 있으면")
    class WithHandle {

        @Test
        @DisplayName("카드에 @이름을 찍는다")
        void displayName() {
            assertThat(showcase("kim").painted(JONGNO).displayName()).isEqualTo("@kim");
        }

        @Test
        @DisplayName("아래 문구에 공개 프로필 주소를 적는다")
        void footer() {
            assertThat(showcase("kim").painted(JONGNO).footer()).isEqualTo("나의 영토 /u/kim");
        }
    }

    @Nested
    @DisplayName("아직 익명이면")
    class Anonymous {

        @Test
        @DisplayName("카드에 '나'라고 찍는다")
        void displayName() {
            assertThat(showcase(null).painted(JONGNO).displayName()).isEqualTo("나");
        }

        @Test
        @DisplayName("아래 문구로 로그인하면 공개 링크가 생긴다고 알려 준다")
        void footer() {
            assertThat(showcase(null).painted(JONGNO).footer()).contains("로그인하면 공개 프로필 링크");
        }
    }
}
