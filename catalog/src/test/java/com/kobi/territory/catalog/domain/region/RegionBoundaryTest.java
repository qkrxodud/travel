package com.kobi.territory.catalog.domain.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kobi.territory.common.model.RegionCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 지역 경계 하나: 점이 안에 드는지(구멍 제외), 밖이면 경계선까지 거리. 가로세로 1도 정사각형 가운데에 0.2도 구멍. */
@DisplayName("지역 경계")
class RegionBoundaryTest {

    private static final List<double[]> 바깥 = List.of(new double[] {127, 36}, new double[] {128, 36}, new double[] {128, 37},
        new double[] {127, 37}, new double[] {127, 36});
    private static final List<double[]> 구멍 = List.of(new double[] {127.4, 36.4}, new double[] {127.6, 36.4},
        new double[] {127.6, 36.6}, new double[] {127.4, 36.6}, new double[] {127.4, 36.4});
    private static final RegionBoundary 네모 = RegionBoundary.of(RegionCode.of("KR-11010"), List.of(List.of(바깥, 구멍)));

    @Nested
    @DisplayName("점이")
    class Point {

        @Test
        @DisplayName("안쪽이면 품는다")
        void inside() {
            assertThat(네모.contains(new GeoPoint(127.2, 36.2))).isTrue();
            assertThat(네모.kilometersFrom(new GeoPoint(127.2, 36.2))).isZero();
        }

        @Test
        @DisplayName("구멍 안이면 품지 않는다")
        void hole() {
            assertThat(네모.contains(new GeoPoint(127.5, 36.5))).isFalse();
        }

        @Test
        @DisplayName("밖이면 가장 가까운 경계선까지 거리를 잰다")
        void outside() {
            GeoPoint 북쪽 = new GeoPoint(127.5, 37.01);
            assertThat(네모.contains(북쪽)).isFalse();
            assertThat(네모.kilometersFrom(북쪽)).isBetween(1.0, 1.2);
        }
    }

    @Test
    @DisplayName("꼭짓점이 셋 미만인 고리나 빈 경계는 만들 수 없다")
    void invalid() {
        assertThatThrownBy(() -> RegionBoundary.of(RegionCode.of("KR-11010"), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegionBoundary.of(RegionCode.of("KR-11010"), List.of(List.of(List.of(new double[] {127, 36})))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("좌표는 경위도 범위 안이어야 한다")
    void pointRange() {
        assertThatThrownBy(() -> new GeoPoint(200, 36)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(127, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
