package com.example.echo.conversation.service;

import com.example.echo.routineplace.entity.RoutinePlace;
import com.example.echo.routineplace.entity.RoutinePlaceStatus;
import com.example.echo.routineplace.repository.RoutinePlaceRepository;
import com.example.echo.user.entity.UserPreferences;
import com.example.echo.user.repository.UserPreferencesRepository;
import com.example.echo.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 경도인지장애 데모 페르소나 시딩 검증.
 * - 사용자 정보/집 좌표/루틴 방문 장소 동의/확정 루틴 장소가 기대값대로 저장되는지 확인
 * - 재실행해도 루틴 장소가 중복 생성되지 않는지(delete-then-insert로 idempotent) 확인
 *
 * 건강 데이터는 시딩하지 않는다 - 대화 시작마다 실제 Health Connect 값으로 즉시 덮어써져 효과가 없다
 * (ConversationDemoSeedService 클래스 주석 참고).
 */
@ExtendWith(MockitoExtension.class)
class ConversationDemoSeedServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserService userService;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @Mock
    private RoutinePlaceRepository routinePlaceRepository;

    private ConversationDemoSeedService seedService;

    @BeforeEach
    void setUp() {
        seedService = new ConversationDemoSeedService(
                userService, userPreferencesRepository, routinePlaceRepository);
    }

    @Test
    @DisplayName("시딩하면 사용자 정보·집 좌표·루틴 장소 동의·확정 루틴 장소가 저장된다")
    void seed_populatesPreferencesAndRoutinePlace() {
        UserPreferences prefs = UserPreferences.builder().userId(USER_ID).build();
        when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        seedService.seed(USER_ID);

        verify(userService).savePreferences(eq(USER_ID), any(com.example.echo.user.dto.UserPreferences.class));
        verify(userService).updateHomeLocation(USER_ID, 37.5301, 127.1238);
        assertThat(prefs.isRoutinePlaceConsent()).isTrue();

        ArgumentCaptor<RoutinePlace> placeCaptor = ArgumentCaptor.forClass(RoutinePlace.class);
        verify(routinePlaceRepository).save(placeCaptor.capture());
        RoutinePlace savedPlace = placeCaptor.getValue();
        assertThat(savedPlace.getStatus()).isEqualTo(RoutinePlaceStatus.CONFIRMED);
        assertThat(savedPlace.getCategory()).isEqualTo("복지관");
        // 앱 전체가 DayOfWeek.name() 전체 요일명 컨벤션을 쓰므로(RoutinePlaceDetectionService,
        // PromptService의 한글 매핑 등) 약어(TUE 등)를 심으면 한글 변환이 깨진다.
        assertThat(savedPlace.getRoutineDays()).isEqualTo("TUESDAY,THURSDAY");
        assertThat(savedPlace.getLatitude()).isEqualTo(ConversationDemoSeedService.ROUTINE_PLACE_LATITUDE);
        assertThat(savedPlace.getLongitude()).isEqualTo(ConversationDemoSeedService.ROUTINE_PLACE_LONGITUDE);
        assertThat(savedPlace.isManualSchedule()).isTrue();
    }

    @Test
    @DisplayName("두 번 연속 시딩해도 루틴 장소를 지우고 다시 심어서 중복 생성되지 않는다")
    void seed_calledTwice_isIdempotentForRoutinePlace() {
        UserPreferences prefs = UserPreferences.builder().userId(USER_ID).build();
        when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        seedService.seed(USER_ID);
        seedService.seed(USER_ID);

        verify(routinePlaceRepository, times(2)).deleteByUserId(USER_ID);
        verify(routinePlaceRepository, times(2)).save(any(RoutinePlace.class));
    }
}
