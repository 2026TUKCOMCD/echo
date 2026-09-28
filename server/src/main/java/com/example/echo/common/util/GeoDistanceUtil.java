package com.example.echo.common.util;

/**
 * 두 좌표 사이의 지표면 거리 계산 유틸리티.
 *
 * 서버(집/외출 분류)와 안드로이드(StayPoint 클러스터링)가 동일한 Haversine 공식을 쓰므로
 * 각 모듈에서 별도로 구현하지 않고 이 클래스로 공유한다.
 */
public final class GeoDistanceUtil {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoDistanceUtil() {
    }

    /**
     * 두 좌표 사이의 거리(미터)를 Haversine 공식으로 계산.
     * 위경도 차이를 지구 곡률을 반영한 실제 지표면 거리로 변환한다.
     */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * 두 좌표 사이의 거리가 반경 이내인지 판정. null 좌표가 있으면 false(판정 불가).
     */
    public static boolean isWithinRadius(Double lat1, Double lon1, Double lat2, Double lon2, double radiusMeters) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return false;
        }
        return haversineMeters(lat1, lon1, lat2, lon2) <= radiusMeters;
    }
}
