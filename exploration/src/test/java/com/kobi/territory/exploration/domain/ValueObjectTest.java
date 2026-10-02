package com.kobi.territory.exploration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.common.model.RegionCode;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ValueObjectTest {

    @Test
    void 메모는_40자까지이고_코드포인트로_센다() {
        assertThat(Memo.of("가".repeat(40)).value()).hasSize(40);
        assertThatThrownBy(() -> Memo.of("가".repeat(41)))
            .isInstanceOf(ExplorationException.class)
            .satisfies(exception -> {
                assertThat(((ExplorationException) exception).error()).isEqualTo(ExplorationError.MEMO_TOO_LONG);
                assertThat(((TerritoryException) exception).kind()).isEqualTo(ErrorKind.INVALID);
            });
        String emoji40 = "🍜".repeat(40); // UTF-16 80자지만 40 코드포인트
        assertThat(Memo.of(emoji40).value()).isEqualTo(emoji40);
    }

    @Test
    void 메모_null과_공백은_빈_메모이고_앞뒤_공백은_자른다() {
        assertThat(Memo.of(null)).isEqualTo(Memo.EMPTY);
        assertThat(Memo.of("   ").isEmpty()).isTrue();
        assertThat(Memo.of("  물회 ").value()).isEqualTo("물회");
        // 공백 포함 41자라도 잘라서 40자 이하면 허용
        assertThat(Memo.of(" " + "가".repeat(40)).value()).hasSize(40);
    }

    @Test
    void 지역_코드는_KR_다섯자리_형식이다() {
        assertThat(RegionCode.of("KR-11010").countryCode()).isEqualTo("KR");
        for (String bad : new String[] {"11010", "KR11010", "kr-11010", "KR-1101", "KR-110100", ""}) {
            assertThatThrownBy(() -> RegionCode.of(bad)).isInstanceOf(TerritoryException.class)
                .satisfies(exception -> assertThat(((TerritoryException) exception).code()).isEqualTo("INVALID_REGION_CODE"));
        }
    }

    @Test
    void 탐험가_id는_UUID다() {
        assertThat(ExplorerId.newId().value()).hasSize(36);
        assertThatThrownBy(() -> ExplorerId.of("nope")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ExplorerId.of("11111111-1111-1111-1111-111111111111"))
            .isEqualTo(ExplorerId.of("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void 방문일은_필수다() {
        assertThatThrownBy(() -> VisitDate.of(null))
            .satisfies(exception -> assertThat(((ExplorationException) exception).error()).isEqualTo(ExplorationError.INVALID_VISIT_DATE));
    }

    @Test
    void 초대코드는_8자_헷갈리는_문자_제외() {
        InviteCode code = InviteCode.generate(new Random(1));
        assertThat(code.value()).hasSize(8).doesNotContain("0", "O", "1", "I");
        assertThatThrownBy(() -> new InviteCode("ABC")).isInstanceOf(ExplorationException.class);
        assertThatThrownBy(() -> new InviteCode("ABCDEFG0")).isInstanceOf(ExplorationException.class);
    }

    @Test
    void 사진_참조는_빈값이면_null이다() {
        assertThat(PhotoRef.ofNullable(" ")).isNull();
        assertThat(PhotoRef.ofNullable("https://x/y.jpg").url()).isEqualTo("https://x/y.jpg");
    }

    @Test
    void 체크인_정책은_상한_1이상() {
        assertThatThrownBy(() -> new CheckInPolicy(0, java.time.Duration.ZERO, false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 지도_id_형식이_틀리면_지도_없음() {
        assertThatThrownBy(() -> MapId.of("x"))
            .satisfies(exception -> assertThat(((ExplorationException) exception).error()).isEqualTo(ExplorationError.MAP_NOT_FOUND));
    }
}
