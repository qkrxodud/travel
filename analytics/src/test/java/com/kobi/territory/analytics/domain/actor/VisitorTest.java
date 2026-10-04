package com.kobi.territory.analytics.domain.actor;

import static com.kobi.territory.analytics.domain.Fixtures.EXPLORER;
import static com.kobi.territory.analytics.domain.Fixtures.HASHER;
import static com.kobi.territory.analytics.domain.Fixtures.VISITOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.TerritoryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("방문과 사람 세기")
class VisitorTest {

    @Nested
    @DisplayName("익명 방문 ID")
    class VisitorIds {

        @Test
        @DisplayName("브라우저가 만든 무작위 값(UUID)을 받는다")
        void uuid() {
            assertThat(VisitorId.of("3f2b8c1e-9a4d-4e6f-8b2a-1c3d5e7f9a0b").value()).hasSize(36);
        }

        @ParameterizedTest(name = "\"{0}\" 은 받지 않는다")
        @ValueSource(strings = {"", "short", "has space in it", "이름은홍길동입니다"})
        @DisplayName("너무 짧거나 글자 모양이 다르면 받지 않는다")
        void invalid(String value) {
            assertThatThrownBy(() -> VisitorId.of(value)).isInstanceOfSatisfying(TerritoryException.class,
                rejected -> assertThat(rejected.code()).isEqualTo("INVALID_VISITOR_ID"));
        }
    }

    @Nested
    @DisplayName("누구로 세는가")
    class Actors {

        @Test
        @DisplayName("토큰이 있는 요청은 그 탐험가로 센다")
        void requestExplorer() {
            assertThat(VisitorLink.NONE.actorFor(VISITOR, EXPLORER)).isEqualTo(ActorKey.ofExplorer(EXPLORER));
        }

        @Test
        @DisplayName("토큰이 없어도 이미 탐험가로 이어진 방문이면 그 탐험가로 센다")
        void linkedVisitor() {
            assertThat(new VisitorLink(EXPLORER, false).actorFor(VISITOR, null)).isEqualTo(ActorKey.ofExplorer(EXPLORER));
        }

        @Test
        @DisplayName("아직 탐험가가 없는 방문은 방문 자신으로 센다")
        void anonymousVisitor() {
            assertThat(VisitorLink.NONE.actorFor(VISITOR, null).value()).isEqualTo("v:" + VISITOR.value());
        }

        @Test
        @DisplayName("이 요청의 탐험가가 그 방문의 탐험가면 방문으로 세던 예전 이벤트를 탐험가로 다시 묶고, 다른 탐험가의 요청이면 묶지 않는다")
        void relinkOnFirstLink() {
            assertThat(new VisitorLink(EXPLORER, true).relinkTarget()).hasValue(ActorKey.ofExplorer(EXPLORER));
            assertThat(new VisitorLink(EXPLORER, false).relinkTarget()).isEmpty();
        }

        @Test
        @DisplayName("같은 기기에서 다른 탐험가로 로그인해도 그 요청은 로그인한 탐험가로 센다")
        void otherExplorerOnSameDevice() {
            ExplorerHash other = HASHER.hash("someone-else");

            assertThat(new VisitorLink(EXPLORER, false).actorFor(VISITOR, other)).isEqualTo(ActorKey.ofExplorer(other));
        }
    }

    @Nested
    @DisplayName("나라와 들어온 길")
    class CountryAndEntry {

        @Test
        @DisplayName("앞단 프록시의 국가 헤더에서 두 글자 나라만 읽고, 모름·익명 망은 버린다")
        void country() {
            assertThat(Country.fromHeader("kr")).hasValue(new Country("KR"));
            assertThat(Country.fromHeader("XX")).isEmpty();
            assertThat(Country.fromHeader("T1")).isEmpty();
            assertThat(Country.fromHeader("Seoul")).isEmpty();
            assertThat(Country.fromHeader(null)).isEmpty();
        }

        @Test
        @DisplayName("공개 카드·프로필 링크로 들어온 길만 카드 유입이다")
        void sharedCardEntries() {
            assertThat(EntryPoint.sharedCardLabels()).containsExactlyInAnyOrder("card", "profile");
            assertThat(EntryPoint.fromLabel("invite")).hasValueSatisfying(entry -> assertThat(entry.fromSharedCard()).isFalse());
            assertThat(EntryPoint.fromLabel("mars")).isEmpty();
        }
    }
}
