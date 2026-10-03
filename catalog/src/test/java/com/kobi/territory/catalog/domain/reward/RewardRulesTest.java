package com.kobi.territory.catalog.domain.reward;

import static org.assertj.core.api.Assertions.assertThat;

import com.kobi.territory.common.model.Rarity;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 체크인·테마 완성 보상 함수 — 탐험(미리보기)과 진행(실제 지급)이 같이 쓴다. 입력은 사실 값뿐(영토를 모른다). 회귀 출처: 2단계 D1. */
@DisplayName("보상 규칙")
class RewardRulesTest {

    private static final RewardRules 규칙 = new RewardRules(Map.of(Rarity.COMMON, 10, Rarity.RARE, 20, Rarity.LEGEND, 50),
        15, 100, 10);

    @Test
    @DisplayName("시·도 첫 발이면서 선점이면 기본·시·도 첫 발·선점 세 줄을 받는다")
    void allThreeLines() {
        assertThat(규칙.checkIn(Rarity.COMMON, true, true)).containsExactly(
            new RewardLine(RewardSource.REGION_BASE, 10), new RewardLine(RewardSource.PROVINCE_FIRST, 15),
            new RewardLine(RewardSource.FIRST_CLAIM, 10));
    }

    @Test
    @DisplayName("둘 다 아니면 희귀도별 기본 XP 한 줄뿐이다")
    void baseOnly() {
        assertThat(규칙.checkIn(Rarity.LEGEND, false, false)).containsExactly(new RewardLine(RewardSource.REGION_BASE, 50));
    }

    @Test
    @DisplayName("선점만이면 기본과 선점 두 줄이다")
    void baseAndClaim() {
        assertThat(규칙.checkIn(Rarity.RARE, false, true)).extracting(RewardLine::amount).containsExactly(20, 10);
    }

    @Test
    @DisplayName("테마 완성 보상은 100이다")
    void themeComplete() {
        assertThat(규칙.setComplete()).isEqualTo(new RewardLine(RewardSource.SET_COMPLETE, 100));
    }
}
