package com.kobi.territory.catalog.domain.region;

import com.kobi.territory.common.model.RegionCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 시·군·구 하나의 경계(regions.geojson 의 Polygon·MultiPolygon). 다각형마다 바깥 고리와 구멍 고리를 가지고, 안쪽 판정은 고리 전체에 대한
 * 짝홀 규칙(even-odd)이라 구멍이 저절로 빠진다. 내부 표현(좌표 배열)을 숨기려고 class.
 */
public final class RegionBoundary {

    private final RegionCode code;
    /** 다각형 → 고리 → 꼭짓점 {경도, 위도}. */
    private final List<List<double[][]>> polygons;

    private RegionBoundary(RegionCode code, List<List<double[][]>> polygons) {
        this.code = Objects.requireNonNull(code, "code");
        this.polygons = polygons;
    }

    /**
     * @param polygons 다각형마다 고리 목록, 고리는 꼭짓점 {경도, 위도} 목록(GeoJSON 좌표 순서). 고리는 꼭짓점 3개 이상.
     */
    public static RegionBoundary of(RegionCode code, List<List<List<double[]>>> polygons) {
        if (polygons == null || polygons.isEmpty()) throw new IllegalArgumentException("경계가 비었다: " + code);
        List<List<double[][]>> copied = new ArrayList<>();
        for (List<List<double[]>> polygon : polygons) {
            List<double[][]> rings = new ArrayList<>();
            for (List<double[]> ring : polygon) {
                if (ring.size() < 3) throw new IllegalArgumentException("경계 고리는 꼭짓점 3개 이상: " + code);
                double[][] vertices = new double[ring.size()][];
                for (int i = 0; i < ring.size(); i++) {
                    double[] vertex = ring.get(i);
                    if (vertex.length < 2) throw new IllegalArgumentException("꼭짓점은 {경도, 위도}: " + code);
                    vertices[i] = new double[] {vertex[0], vertex[1]};
                }
                rings.add(vertices);
            }
            if (rings.isEmpty()) throw new IllegalArgumentException("고리 없는 다각형: " + code);
            copied.add(List.copyOf(rings));
        }
        return new RegionBoundary(code, List.copyOf(copied));
    }

    public RegionCode code() {
        return code;
    }

    /** 점이 경계 안인지(어느 다각형이든, 구멍 제외). 경계선 위의 점은 어느 쪽으로든 판정될 수 있다. */
    public boolean contains(GeoPoint point) {
        return polygons.stream().anyMatch(rings -> insideRings(rings, point));
    }

    /** 경계 밖이면 가장 가까운 경계선까지의 거리(km, 평면 근사), 안이면 0. */
    public double kilometersFrom(GeoPoint point) {
        if (contains(point)) return 0;
        double nearest = Double.MAX_VALUE;
        for (List<double[][]> rings : polygons) {
            for (double[][] ring : rings) {
                for (int i = 0; i < ring.length; i++) {
                    double[] from = ring[i];
                    double[] to = ring[(i + 1) % ring.length];
                    nearest = Math.min(nearest, segmentDistance(point, from, to));
                }
            }
        }
        return nearest;
    }

    private static boolean insideRings(List<double[][]> rings, GeoPoint point) {
        boolean inside = false;
        for (double[][] ring : rings) {
            for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
                double[] current = ring[i];
                double[] previous = ring[j];
                boolean crosses = (current[1] > point.latitude()) != (previous[1] > point.latitude());
                if (crosses) {
                    double crossingLongitude = (previous[0] - current[0]) * (point.latitude() - current[1]) / (previous[1] - current[1])
                        + current[0];
                    if (point.longitude() < crossingLongitude) inside = !inside;
                }
            }
        }
        return inside;
    }

    /** 점에서 선분까지의 거리(km) — 점을 원점으로 한 평면 근사. */
    private static double segmentDistance(GeoPoint point, double[] from, double[] to) {
        double fromEast = point.eastKilometers(from[0]);
        double fromNorth = point.northKilometers(from[1]);
        double toEast = point.eastKilometers(to[0]);
        double toNorth = point.northKilometers(to[1]);
        double segmentEast = toEast - fromEast;
        double segmentNorth = toNorth - fromNorth;
        double lengthSquared = segmentEast * segmentEast + segmentNorth * segmentNorth;
        double along = lengthSquared == 0 ? 0 : Math.clamp(-(fromEast * segmentEast + fromNorth * segmentNorth) / lengthSquared, 0.0, 1.0);
        double closestEast = fromEast + along * segmentEast;
        double closestNorth = fromNorth + along * segmentNorth;
        return Math.hypot(closestEast, closestNorth);
    }
}
