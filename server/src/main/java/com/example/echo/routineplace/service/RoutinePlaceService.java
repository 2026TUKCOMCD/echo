package com.example.echo.routineplace.service;

import com.example.echo.location.dto.GeocodingResult;
import com.example.echo.location.service.GeocodingService;
import com.example.echo.routineplace.dto.ConsentResponse;
import com.example.echo.routineplace.dto.RoutinePlaceConfirmRequest;
import com.example.echo.routineplace.dto.RoutinePlaceInfo;
import com.example.echo.routineplace.dto.RoutinePlaceResponse;
import com.example.echo.routineplace.entity.RoutinePlace;
import com.example.echo.routineplace.entity.RoutinePlaceStatus;
import com.example.echo.routineplace.repository.RoutinePlaceRepository;
import com.example.echo.routineplace.repository.VisitOccurrenceRepository;
import com.example.echo.user.entity.UserPreferences;
import com.example.echo.user.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 루틴 방문 장소 후보/확정 관리, 동의 상태 관리.
 *
 * 최소 수집 원칙: 정확한 상호명/주소는 저장하지 않으므로, 후보 확인 화면에서 사람이 알아볼 수
 * 있는 주소가 필요할 때만 그때그때 역지오코딩한다(UserController.previewHomeAddress와 동일한 패턴).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutinePlaceService {

    private final RoutinePlaceRepository routinePlaceRepository;
    private final VisitOccurrenceRepository visitOccurrenceRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final GeocodingService geocodingService;
    private final RoutinePlaceDetectionService routinePlaceDetectionService;

    @Transactional(readOnly = true)
    public List<RoutinePlaceResponse> getCandidates(Long userId) {
        return routinePlaceRepository.findByUserIdAndStatus(userId, RoutinePlaceStatus.SUGGESTED).stream()
                .map(this::toResponseWithPreview)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoutinePlaceResponse> getConfirmedPlaces(Long userId) {
        return routinePlaceRepository.findByUserIdAndStatus(userId, RoutinePlaceStatus.CONFIRMED).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 대화 파이프라인(ContextService/LocationService)에서 쓰는 경량 확정 장소 목록.
     * 동의를 철회하면 setConsent(false)가 관련 row를 전량 삭제하므로 이 조회는 별도 동의 체크가 필요 없다.
     */
    @Transactional(readOnly = true)
    public List<RoutinePlaceInfo> getConfirmedPlaceInfos(Long userId) {
        return routinePlaceRepository.findByUserIdAndStatus(userId, RoutinePlaceStatus.CONFIRMED).stream()
                .map(place -> RoutinePlaceInfo.builder()
                        .id(place.getId())
                        .latitude(place.getLatitude())
                        .longitude(place.getLongitude())
                        .radiusMeters(place.getRadiusMeters())
                        .category(place.getCategory())
                        .routineDays(parseDays(place.getRoutineDays()))
                        .routineTimeRangeStart(place.getRoutineTimeRangeStart())
                        .routineTimeRangeEnd(place.getRoutineTimeRangeEnd())
                        .build())
                .toList();
    }

    @Transactional
    public RoutinePlaceResponse confirm(Long userId, Long placeId, RoutinePlaceConfirmRequest request) {
        RoutinePlace place = getOwnedPlace(userId, placeId);
        place.confirm(request.getCategory());
        applyScheduleIfPresent(place, request);
        return toResponse(place);
    }

    @Transactional
    public RoutinePlaceResponse update(Long userId, Long placeId, RoutinePlaceConfirmRequest request) {
        RoutinePlace place = getOwnedPlace(userId, placeId);
        place.updateCategory(request.getCategory());
        applyScheduleIfPresent(place, request);
        return toResponse(place);
    }

    private void applyScheduleIfPresent(RoutinePlace place, RoutinePlaceConfirmRequest request) {
        applyScheduleIfPresent(place, request.getRoutineDays(), request.getRoutineTimeRangeStart(),
                request.getRoutineTimeRangeEnd());
    }

    private void applyScheduleIfPresent(RoutinePlace place, List<DayOfWeek> days,
                                         java.time.LocalTime start, java.time.LocalTime end) {
        if (days == null && start == null && end == null) {
            return;
        }
        String daysCsv = days == null || days.isEmpty()
                ? null
                : days.stream().map(Enum::name).collect(Collectors.joining(","));
        place.applyManualSchedule(daysCsv, start, end);
    }

    /**
     * 후보는 거절(DISMISSED로 전환 - 같은 좌표 재제안 방지), 확정된 장소는 완전 삭제.
     */
    @Transactional
    public void delete(Long userId, Long placeId) {
        RoutinePlace place = getOwnedPlace(userId, placeId);
        if (place.getStatus() == RoutinePlaceStatus.CONFIRMED) {
            routinePlaceRepository.delete(place);
        } else if (place.getStatus() == RoutinePlaceStatus.SUGGESTED) {
            place.dismiss();
        }
    }

    /**
     * ContextService가 방문 이력 기록 여부를 결정할 때 쓰는 게이트 - 서버가 직접 동의를 확인한다
     * (클라이언트가 보낸 값을 신뢰하지 않음).
     */
    @Transactional(readOnly = true)
    public boolean hasConsent(Long userId) {
        return userPreferencesRepository.findByUserId(userId)
                .map(UserPreferences::isRoutinePlaceConsent)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public ConsentResponse getConsent(Long userId) {
        return userPreferencesRepository.findByUserId(userId)
                .map(prefs -> ConsentResponse.builder()
                        .consented(prefs.isRoutinePlaceConsent())
                        .consentedAt(prefs.getRoutinePlaceConsentAt())
                        .build())
                .orElse(ConsentResponse.builder().consented(false).build());
    }

    /**
     * 감지 기능 on/off. off(consented=false)로 끄면 새 방문 기록·후보 생성을 멈추고
     * 감지용 임시 데이터(VisitOccurrence)와 아직 확인 안 한 후보(SUGGESTED)를 정리하지만,
     * 사용자가 이미 확정한 장소(CONFIRMED)는 남긴다 - 완전 삭제를 원하면 withdrawConsent 사용.
     */
    @Transactional
    public ConsentResponse setConsent(Long userId, boolean consented) {
        UserPreferences prefs = userPreferencesRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("온보딩이 완료되지 않았습니다."));

        prefs.updateRoutinePlaceConsent(consented);

        if (!consented) {
            visitOccurrenceRepository.deleteByUserId(userId);
            routinePlaceRepository.deleteByUserIdAndStatus(userId, RoutinePlaceStatus.SUGGESTED);
            log.info("[루틴장소] 감지 기능 끔 - 임시 데이터/미확인 후보 정리 (확정 장소는 유지) - userId: {}", userId);
        }

        return ConsentResponse.builder()
                .consented(prefs.isRoutinePlaceConsent())
                .consentedAt(prefs.getRoutinePlaceConsentAt())
                .build();
    }

    /**
     * 완전 철회 - 확정된 장소를 포함해 저장된 관련 데이터를 전량 즉시 파기한다
     * (개인정보보호법상 동의 철회 시 파기 원칙에 따른 별도 액션. setConsent(false)와 달리
     * 확정 장소도 남기지 않는다).
     */
    @Transactional
    public ConsentResponse withdrawConsent(Long userId) {
        UserPreferences prefs = userPreferencesRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("온보딩이 완료되지 않았습니다."));

        prefs.updateRoutinePlaceConsent(false);
        visitOccurrenceRepository.deleteByUserId(userId);
        routinePlaceRepository.deleteByUserId(userId);
        log.info("[루틴장소] 완전 철회로 데이터 전량 파기 - userId: {}", userId);

        return ConsentResponse.builder()
                .consented(false)
                .consentedAt(null)
                .build();
    }

    /**
     * 개발/테스트 편의용: 매일 새벽 스케줄러를 기다리지 않고 패턴 감지를 즉시 실행한다.
     * UserController.resetConversationData와 동일하게 본인 계정에 한해 인증된 사용자만 호출 가능.
     */
    public void detectNow(Long userId) {
        routinePlaceDetectionService.detectForUser(userId);
    }

    private RoutinePlace getOwnedPlace(Long userId, Long placeId) {
        return routinePlaceRepository.findByIdAndUserId(placeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("루틴 방문 장소를 찾을 수 없습니다. id=" + placeId));
    }

    private RoutinePlaceResponse toResponse(RoutinePlace place) {
        return RoutinePlaceResponse.builder()
                .id(place.getId())
                .status(place.getStatus())
                .category(place.getCategory())
                .routineDays(parseDays(place.getRoutineDays()))
                .routineTimeRangeStart(place.getRoutineTimeRangeStart())
                .routineTimeRangeEnd(place.getRoutineTimeRangeEnd())
                .occurrenceCount(place.getOccurrenceCount())
                .lastDetectedAt(place.getLastDetectedAt())
                .confirmedAt(place.getConfirmedAt())
                .manualSchedule(place.isManualSchedule())
                .build();
    }

    private RoutinePlaceResponse toResponseWithPreview(RoutinePlace place) {
        // reverseGeocode는 실패 시 내부적으로 처리해 null 필드를 가진 결과를 반환하므로 별도 예외 처리 불필요
        RoutinePlaceResponse response = toResponse(place);
        GeocodingResult preview = geocodingService.reverseGeocode(place.getLatitude(), place.getLongitude());
        response.setPreviewAddress(preview.getAddress());
        return response;
    }

    private List<String> parseDays(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).collect(Collectors.toList());
    }
}
