package com.example.echo.routineplace.service;

import com.example.echo.location.dto.GeocodingResult;
import com.example.echo.location.service.GeocodingService;
import com.example.echo.routineplace.dto.ConsentResponse;
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
    public RoutinePlaceResponse confirm(Long userId, Long placeId, String category) {
        RoutinePlace place = getOwnedPlace(userId, placeId);
        place.confirm(category);
        return toResponse(place);
    }

    @Transactional
    public RoutinePlaceResponse updateCategory(Long userId, Long placeId, String category) {
        RoutinePlace place = getOwnedPlace(userId, placeId);
        place.updateCategory(category);
        return toResponse(place);
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
     * 동의/철회. 철회 시(consented=false) 저장된 방문 이력·루틴 장소를 즉시 전량 파기한다
     * (개인정보보호법상 목적 달성/동의 철회 시 파기 원칙).
     */
    @Transactional
    public ConsentResponse setConsent(Long userId, boolean consented) {
        UserPreferences prefs = userPreferencesRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("온보딩이 완료되지 않았습니다."));

        prefs.updateRoutinePlaceConsent(consented);

        if (!consented) {
            visitOccurrenceRepository.deleteByUserId(userId);
            routinePlaceRepository.deleteByUserId(userId);
            log.info("[루틴장소] 동의 철회로 데이터 전량 파기 - userId: {}", userId);
        }

        return ConsentResponse.builder()
                .consented(prefs.isRoutinePlaceConsent())
                .consentedAt(prefs.getRoutinePlaceConsentAt())
                .build();
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
