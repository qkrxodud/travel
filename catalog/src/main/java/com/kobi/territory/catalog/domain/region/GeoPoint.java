package com.kobi.territory.catalog.domain.region;

/**
 * 경위도 한 점(WGS84, 도 단위). 외부 자료(TourAPI 축제 좌표 mapx·mapy)를 우리 시·군·구 경계에 넣을 때 쓴다. 값 하나를 감싸 검증만 하는 값이라
 * record.
 *
 * @param longitude 경도(동경 +)
 * @param latitude  위도(북위 +)
 */
public record GeoPoint(double longitude, double latitude) {

    private static final double KM_PER_DEGREE_LATITUDE = 110.574;
    private static final double KM_PER_DEGREE_LONGITUDE_AT_EQUATOR = 111.320;

    public GeoPoint {
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("경도 범위 밖: " + longitude);
        }
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) throw new IllegalArgumentException("위도 범위 밖: " + latitude);
    }

    /** 이 점을 원점으로 한 평면 근사(등장방형)에서 다른 경위도의 동쪽 거리(km). 수십 km 안에서 쓰는 근사다. */
    double eastKilometers(double otherLongitude) {
        return (otherLongitude - longitude) * KM_PER_DEGREE_LONGITUDE_AT_EQUATOR * Math.cos(Math.toRadians(latitude));
    }

    /** 이 점을 원점으로 한 평면 근사에서 다른 위도의 북쪽 거리(km). */
    double northKilometers(double otherLatitude) {
        return (otherLatitude - latitude) * KM_PER_DEGREE_LATITUDE;
    }
}
