package com.example.echo.conversation.service;

import com.example.echo.context.service.ContextService;
import com.example.echo.diary.entity.Diary;
import com.example.echo.diary.entity.DiaryStatus;
import com.example.echo.diary.repository.DiaryRepository;
import com.example.echo.memory.entity.Memory;
import com.example.echo.memory.repository.MemoryRepository;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 경도인지장애 데모 페르소나 시딩 검증.
 * - 사용자 정보/집 좌표/루틴 방문 장소 동의/확정 루틴 장소가 기대값대로 저장되는지 확인
 * - 최근 3일(D-3~D-1) 일기와 장기기억이 심어지고, 오늘 날짜 일기는 심지 않는지 확인
 * - 재실행해도 루틴 장소/일기/장기기억이 중복 생성되지 않는지(delete-then-insert로 idempotent) 확인
 * - 진행 중인 대화 세션도 함께 종료되는지(ContextService.finalizeContext) 확인
 *
 * 건강 데이터는 시딩하지 않는다 - 대화 시작마다 실제 Health Connect 값으로 즉시 덮어써져 효과가 없다
 * (ConversationDemoSeedService 클래스 주석 참고).
 */
@ExtendWith(MockitoExtension.class)
class ConversationDemoSeedServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 9, 17);

    @Mock
    private UserService userService;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @Mock
    private RoutinePlaceRepository routinePlaceRepository;

    @Mock
    private DiaryRepository diaryRepository;

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private ContextService contextService;

    private ConversationDemoSeedService seedService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                FIXED_TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        seedService = new ConversationDemoSeedService(
                userService, userPreferencesRepository, routinePlaceRepository,
                diaryRepository, memoryRepository, contextService, clock);
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

    @Test
    @DisplayName("시딩하면 오늘을 제외한 최근 3일(D-3~D-1) SUCCESS 일기가 저장된다")
    void seed_populatesRecentDiariesExcludingToday() {
        UserPreferences prefs = UserPreferences.builder().userId(USER_ID).build();
        when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        seedService.seed(USER_ID);

        verify(diaryRepository).deleteByUserId(USER_ID);
        ArgumentCaptor<Diary> diaryCaptor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryRepository, times(3)).save(diaryCaptor.capture());

        List<Diary> savedDiaries = diaryCaptor.getAllValues();
        assertThat(savedDiaries).extracting(Diary::getDiaryDate)
                .containsExactlyInAnyOrder(
                        FIXED_TODAY.minusDays(3), FIXED_TODAY.minusDays(2), FIXED_TODAY.minusDays(1));
        assertThat(savedDiaries).noneMatch(diary -> diary.getDiaryDate().isEqual(FIXED_TODAY));
        assertThat(savedDiaries).allMatch(diary -> diary.getStatus() == DiaryStatus.SUCCESS);
        assertThat(savedDiaries).allMatch(diary -> diary.getContent() != null && !diary.getContent().isBlank());
    }

    @Test
    @DisplayName("시딩하면 사용자 정보(직업/가족/취미/루틴 장소)와 어긋나지 않는 장기기억이 저장된다")
    void seed_populatesMemoriesConsistentWithPersona() {
        UserPreferences prefs = UserPreferences.builder().userId(USER_ID).build();
        when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        seedService.seed(USER_ID);

        verify(memoryRepository).deleteByUserId(USER_ID);
        ArgumentCaptor<List<Memory>> memoryCaptor = ArgumentCaptor.forClass(List.class);
        verify(memoryRepository).saveAll(memoryCaptor.capture());

        List<Memory> savedMemories = memoryCaptor.getValue();
        assertThat(savedMemories).isNotEmpty();
        assertThat(savedMemories).allMatch(memory -> memory.getContent() != null && !memory.getContent().isBlank());
        assertThat(savedMemories).extracting(Memory::getTopic).contains("직업", "가족", "취미");
    }

    @Test
    @DisplayName("두 번 연속 시딩해도 일기·장기기억을 지우고 다시 심어서 중복 생성되지 않는다")
    void seed_calledTwice_isIdempotentForDiariesAndMemories() {
        UserPreferences prefs = UserPreferences.builder().userId(USER_ID).build();
        when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        seedService.seed(USER_ID);
        seedService.seed(USER_ID);

        verify(diaryRepository, times(2)).deleteByUserId(USER_ID);
        verify(diaryRepository, times(6)).save(any(Diary.class));
        verify(memoryRepository, times(2)).deleteByUserId(USER_ID);
        verify(memoryRepository, times(2)).saveAll(any(List.class));
    }

    @Test
    @DisplayName("시딩하면 이전 관람자의 진행 중인 대화 세션이 종료된다")
    void seed_finalizesInProgressConversationSession() {
        UserPreferences prefs = UserPreferences.builder().userId(USER_ID).build();
        when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        seedService.seed(USER_ID);

        verify(contextService).finalizeContext(USER_ID);
    }
}
