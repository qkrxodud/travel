package com.kobi.territory.sharing.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 카드 지도 실루엣 — 카탈로그 GeoJSON(250 지역)을 메르카토르로 투영해 카드 왼쪽 영역에 맞춘 Path2D 로 한 번 만들어 둔다
 * (프로토타입 d3.geoMercator().fitExtent 와 같은 배치).
 */
final class KoreaSilhouette {

    private final Map<String, Path2D> pathByCode;

    private KoreaSilhouette(Map<String, Path2D> pathByCode) {
        this.pathByCode = Collections.unmodifiableMap(pathByCode);
    }

    static KoreaSilhouette fit(String geoJson, Rectangle2D extent) {
        List<Region> regions = parse(geoJson);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Region region : regions) {
            for (List<double[]> ring : region.rings()) {
                for (double[] point : ring) {
                    minX = Math.min(minX, point[0]);
                    maxX = Math.max(maxX, point[0]);
                    minY = Math.min(minY, point[1]);
                    maxY = Math.max(maxY, point[1]);
                }
            }
        }
        double scale = Math.min(extent.getWidth() / (maxX - minX), extent.getHeight() / (maxY - minY));
        double offsetX = extent.getX() + (extent.getWidth() - (maxX - minX) * scale) / 2;
        double offsetY = extent.getY() + (extent.getHeight() - (maxY - minY) * scale) / 2;
        Map<String, Path2D> paths = new LinkedHashMap<>();
        for (Region region : regions) {
            Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
            for (List<double[]> ring : region.rings()) {
                for (int i = 0; i < ring.size(); i++) {
                    double projectedX = offsetX + (ring.get(i)[0] - minX) * scale;
                    double projectedY = offsetY + (maxY - ring.get(i)[1]) * scale;
                    if (i == 0) path.moveTo(projectedX, projectedY);
                    else path.lineTo(projectedX, projectedY);
                }
                path.closePath();
            }
            paths.put(region.code(), path);
        }
        return new KoreaSilhouette(paths);
    }

    Map<String, Path2D> paths() {
        return pathByCode;
    }

    Optional<Point2D> center(String code) {
        return Optional.ofNullable(pathByCode.get(code)).map(path -> {
            Rectangle2D bounds = path.getBounds2D();
            return new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
        });
    }

    private record Region(String code, List<List<double[]>> rings) {}

    /** 경도 → x(라디안), 위도 → 메르카토르 y. */
    private static double[] project(JsonNode coordinate) {
        double lon = Math.toRadians(coordinate.get(0).asDouble());
        double lat = Math.toRadians(coordinate.get(1).asDouble());
        return new double[] {lon, Math.log(Math.tan(Math.PI / 4 + lat / 2))};
    }

    private static List<Region> parse(String geoJson) {
        try {
            JsonNode root = new ObjectMapper().readTree(geoJson);
            List<Region> regions = new ArrayList<>();
            for (JsonNode feature : root.get("features")) {
                String code = feature.get("properties").get("code").asText();
                JsonNode geometry = feature.get("geometry");
                List<List<double[]>> rings = new ArrayList<>();
                if ("Polygon".equals(geometry.get("type").asText())) {
                    addPolygon(geometry.get("coordinates"), rings);
                } else {
                    for (JsonNode polygon : geometry.get("coordinates")) addPolygon(polygon, rings);
                }
                regions.add(new Region(code, rings));
            }
            return regions;
        } catch (IOException malformed) {
            throw new UncheckedIOException("지역 GeoJSON 을 읽지 못했습니다", malformed);
        }
    }

    private static void addPolygon(JsonNode polygon, List<List<double[]>> rings) {
        for (JsonNode ring : polygon) {
            List<double[]> points = new ArrayList<>();
            for (JsonNode coordinate : ring) points.add(project(coordinate));
            rings.add(points);
        }
    }
}
