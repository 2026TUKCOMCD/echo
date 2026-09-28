package com.example.echo.routineplace.service;

import com.example.echo.common.util.GeoDistanceUtil;
import com.example.echo.location.dto.RawLocationData;
import com.example.echo.location.dto.RawVisitedPlace;
import com.example.echo.location.service.LocationService;
import com.example.echo.routineplace.entity.VisitOccurrence;
import com.example.echo.routineplace.repository.VisitOccurrenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 루틴 방문 장소 패턴 감지를 위한 원재료(VisitOccurrence) 기록.
 *
 * 호출 여부는 반드시 호출부(ContextService)에서 사용자의 routinePlaceConsent를 확인한 뒤에만
 * 호출해야 한다 - 이 서비스 자체는 동의 여부를 다시 검사하지 않는다(단일 책임: 기록만 담당).
 * 동의 게이팅을 이중으로 하지 않는 대신, 호출부 쪽 주석/테스트로 "동의 없이는 절대 호출되지
 * 않는다"를 보장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitOccurrenceRecordingService {

    private final VisitOccurrenceRepository visitOccurrenceRepository;

    /**
     * 오늘의 방문 장소 중 집이 아닌 곳만 발생 이력으로 기록한다.
     *
     * 같은 날짜에 대해 재호출되면(대화 재시작 등) 먼저 그날 기록을 지우고 다시 쌓아
     * 멱등성을 보장한다(중복 누적 방지).
     *
     * @param userId        사용자 ID
     * @param raw           오늘 앱에서 전송된 원시 위치 데이터 (null 허용)
     * @param homeLatitude  등록된 집 위도 (null이면 전부 기록 대상)
     * @param homeLongitude 등록된 집 경도
     * @param visitDate     기록 기준 날짜 (Asia/Seoul 기준 오늘 - 호출부에서 Clock으로 계산해 전달)
     */
    @Transactional
    public void recordOccurrences(Long userId, RawLocationData raw, Double homeLatitude, Double homeLongitude,
                                   LocalDate visitDate) {
        visitOccurrenceRepository.deleteByUserIdAndVisitDate(userId, visitDate);

        if (raw == null || raw.getVisitedPlaces() == null || raw.getVisitedPlaces().isEmpty()) {
            return;
        }

        int recorded = 0;
        for (RawVisitedPlace place : raw.getVisitedPlaces()) {
            boolean isHome = GeoDistanceUtil.isWithinRadius(
                    place.getLatitude(), place.getLongitude(),
                    homeLatitude, homeLongitude,
                    LocationService.HOME_MATCH_RADIUS_METERS);
            if (isHome) {
                continue;
            }

            visitOccurrenceRepository.save(VisitOccurrence.builder()
                    .userId(userId)
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .visitDate(visitDate)
                    .visitStartTime(place.getVisitStartTime())
                    .visitEndTime(place.getVisitEndTime())
                    .stayDurationMinutes(place.getStayDurationMinutes())
                    .build());
            recorded++;
        }

        log.debug("[루틴장소] 방문 이력 기록 - userId: {}, date: {}, 기록 {}건 (집 제외)",
                userId, visitDate, recorded);
    }
}
