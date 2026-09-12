package com.example.echo.routineplace.service;

import com.example.echo.location.service.GeocodingService;
import com.example.echo.routineplace.dto.ConsentResponse;
import com.example.echo.routineplace.entity.RoutinePlace;
import com.example.echo.routineplace.entity.RoutinePlaceStatus;
import com.example.echo.routineplace.repository.RoutinePlaceRepository;
import com.example.echo.routineplace.repository.VisitOccurrenceRepository;
import com.example.echo.user.entity.UserPreferences;
import com.example.echo.user.repository.UserPreferencesRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 루틴 방문 장소 동의/철회 및 후보 거절·확정 삭제 검증.
 *
 * 동의 철회 시(개인정보보호법상 파기 원칙) 저장된 방문 이력·루틴 장소가 전량 삭제되는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class RoutinePlaceServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private RoutinePlaceRepository routinePlaceRepository;

    @Mock
    private VisitOccurrenceRepository visitOccurrenceRepository;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @Mock
    private GeocodingService geocodingService;

    @InjectMocks
    private RoutinePlaceService routinePlaceService;

    @Test
    @DisplayName("동의 철회 시 방문 이력·루틴 장소를 전량 파기한다")
    void revokingConsent_purgesAllRelatedData() {
        UserPreferences prefs = UserPreferences.builder().userId(USER_ID).build();
        when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        ConsentResponse response = routinePlaceService.setConsent(USER_ID, false);

        verify(visitOccurrenceRepository, times(1)).deleteByUserId(USER_ID);
        verify(routinePlaceRepository, times(1)).deleteByUserId(USER_ID);
        assertThat(response.isConsented()).isFalse();
    }

    @Test
    @DisplayName("동의 시에는 기존 데이터를 파기하지 않는다")
    void grantingConsent_doesNotPurgeData() {
        UserPreferences prefs = UserPreferences.builder().userId(USER_ID).build();
        when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        routinePlaceService.setConsent(USER_ID, true);

        verify(visitOccurrenceRepository, never()).deleteByUserId(anyLong());
        verify(routinePlaceRepository, never()).deleteByUserId(anyLong());
    }

    @Test
    @DisplayName("후보(SUGGESTED)를 삭제하면 완전 삭제가 아니라 DISMISSED로 전환한다")
    void deletingCandidate_dismissesInsteadOfHardDelete() {
        RoutinePlace candidate = RoutinePlace.builder()
                .userId(USER_ID).status(RoutinePlaceStatus.SUGGESTED).build();
        when(routinePlaceRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(candidate));

        routinePlaceService.delete(USER_ID, 10L);

        assertThat(candidate.getStatus()).isEqualTo(RoutinePlaceStatus.DISMISSED);
        verify(routinePlaceRepository, never()).delete(any(RoutinePlace.class));
    }

    @Test
    @DisplayName("확정(CONFIRMED)된 장소를 삭제하면 row를 완전히 제거한다")
    void deletingConfirmedPlace_hardDeletes() {
        RoutinePlace confirmed = RoutinePlace.builder()
                .userId(USER_ID).status(RoutinePlaceStatus.CONFIRMED).category("회사").build();
        when(routinePlaceRepository.findByIdAndUserId(20L, USER_ID)).thenReturn(Optional.of(confirmed));

        routinePlaceService.delete(USER_ID, 20L);

        verify(routinePlaceRepository, times(1)).delete(confirmed);
    }
}
