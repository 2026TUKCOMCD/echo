package com.example.echo.routineplace.service;

import com.example.echo.routineplace.entity.RoutinePlace;
import com.example.echo.routineplace.entity.RoutinePlaceStatus;
import com.example.echo.routineplace.entity.VisitOccurrence;
import com.example.echo.routineplace.repository.RoutinePlaceRepository;
import com.example.echo.routineplace.repository.VisitOccurrenceRepository;
import com.example.echo.user.repository.UserPreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 루틴 방문 장소 패턴 감지 검증.
 *
 * "같은 요일에 최소 3회, 서로 다른 3개 주(week)에서 관측"되어야 후보가 생성되는지,
 * 기준 미달이면 생성되지 않는지, 거절(DISMISSED)된 장소는 재제안되지 않는지를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class RoutinePlaceDetectionServiceTest {

    private static final Long USER_ID = 1L;
    private static final double LAT = 37.50;
    private static final double LNG = 127.05;

    @Mock
    private VisitOccurrenceRepository visitOccurrenceRepository;

    @Mock
    private RoutinePlaceRepository routinePlaceRepository;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    private RoutinePlaceDetectionService detectionService;

    @BeforeEach
    void setUp() {
        // "오늘"을 2026-06-17(base+2주+2일)로 고정 - base~base+2주 방문 이력이 모두
        // WINDOW_WEEKS(6주) 이내에 들어오도록 함
        Clock clock = Clock.fixed(
                LocalDate.of(2026, 6, 17).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul"));
        detectionService = new RoutinePlaceDetectionService(
                visitOccurrenceRepository, routinePlaceRepository, userPreferencesRepository, clock);
    }

    private VisitOccurrence occurrenceOn(LocalDate date) {
        return VisitOccurrence.builder()
                .userId(USER_ID)
                .latitude(LAT)
                .longitude(LNG)
                .visitDate(date)
                .visitStartTime(LocalTime.of(14, 0))
                .visitEndTime(LocalTime.of(16, 0))
                .stayDurationMinutes(120)
                .build();
    }

    @Test
    @DisplayName("같은 요일 3회 이상 & 서로 다른 3개 주 이상 방문 → 새 후보(SUGGESTED) 생성")
    void threeDistinctWeeksSameWeekday_createsSuggestedCandidate() {
        LocalDate base = LocalDate.of(2026, 6, 1);
        List<VisitOccurrence> occurrences = List.of(
                occurrenceOn(base),
                occurrenceOn(base.plusWeeks(1)),
                occurrenceOn(base.plusWeeks(2))
        );
        when(visitOccurrenceRepository.findByUserIdAndVisitDateAfter(eq(USER_ID), any())).thenReturn(occurrences);
        when(routinePlaceRepository.findByUserId(USER_ID)).thenReturn(List.of());

        detectionService.detectForUser(USER_ID);

        ArgumentCaptor<RoutinePlace> captor = ArgumentCaptor.forClass(RoutinePlace.class);
        verify(routinePlaceRepository, times(1)).save(captor.capture());
        RoutinePlace created = captor.getValue();
        assertThat(created.getStatus()).isEqualTo(RoutinePlaceStatus.SUGGESTED);
        assertThat(created.getRoutineDays()).isEqualTo(base.getDayOfWeek().name());
        assertThat(created.getOccurrenceCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("서로 다른 주가 2개뿐이면 후보를 생성하지 않는다")
    void onlyTwoDistinctWeeks_noCandidateCreated() {
        LocalDate base = LocalDate.of(2026, 6, 1);
        List<VisitOccurrence> occurrences = List.of(
                occurrenceOn(base),
                occurrenceOn(base.plusWeeks(1))
        );
        when(visitOccurrenceRepository.findByUserIdAndVisitDateAfter(eq(USER_ID), any())).thenReturn(occurrences);
        when(routinePlaceRepository.findByUserId(USER_ID)).thenReturn(List.of());

        detectionService.detectForUser(USER_ID);

        verify(routinePlaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 거절(DISMISSED)된 장소와 매칭되면 재제안하지 않는다")
    void dismissedPlace_neverReSuggested() {
        LocalDate base = LocalDate.of(2026, 6, 1);
        List<VisitOccurrence> occurrences = List.of(
                occurrenceOn(base),
                occurrenceOn(base.plusWeeks(1)),
                occurrenceOn(base.plusWeeks(2))
        );
        when(visitOccurrenceRepository.findByUserIdAndVisitDateAfter(eq(USER_ID), any())).thenReturn(occurrences);

        RoutinePlace dismissed = RoutinePlace.builder()
                .userId(USER_ID)
                .latitude(LAT)
                .longitude(LNG)
                .status(RoutinePlaceStatus.DISMISSED)
                .build();
        when(routinePlaceRepository.findByUserId(USER_ID)).thenReturn(new java.util.ArrayList<>(List.of(dismissed)));

        detectionService.detectForUser(USER_ID);

        verify(routinePlaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("기존 확정 장소와 매칭되면 새로 만들지 않고 통계만 갱신한다")
    void matchedConfirmedPlace_refreshesStatsInsteadOfCreating() {
        LocalDate base = LocalDate.of(2026, 6, 1);
        List<VisitOccurrence> occurrences = List.of(
                occurrenceOn(base),
                occurrenceOn(base.plusWeeks(1)),
                occurrenceOn(base.plusWeeks(2))
        );
        when(visitOccurrenceRepository.findByUserIdAndVisitDateAfter(eq(USER_ID), any())).thenReturn(occurrences);

        RoutinePlace confirmed = RoutinePlace.builder()
                .userId(USER_ID)
                .latitude(LAT)
                .longitude(LNG)
                .status(RoutinePlaceStatus.CONFIRMED)
                .category("회사")
                .build();
        when(routinePlaceRepository.findByUserId(USER_ID)).thenReturn(new java.util.ArrayList<>(List.of(confirmed)));

        detectionService.detectForUser(USER_ID);

        ArgumentCaptor<RoutinePlace> captor = ArgumentCaptor.forClass(RoutinePlace.class);
        verify(routinePlaceRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(confirmed);
        assertThat(confirmed.getStatus()).isEqualTo(RoutinePlaceStatus.CONFIRMED);
        assertThat(confirmed.getCategory()).isEqualTo("회사"); // 라벨은 그대로 유지
        assertThat(confirmed.getOccurrenceCount()).isEqualTo(3); // 통계만 갱신
    }

    @Test
    @DisplayName("사용자가 요일/시간대를 직접 수정한 장소는 재계산이 덮어쓰지 않는다")
    void manuallyEditedSchedule_notOverwrittenByDetection() {
        LocalDate base = LocalDate.of(2026, 6, 1);
        List<VisitOccurrence> occurrences = List.of(
                occurrenceOn(base),
                occurrenceOn(base.plusWeeks(1)),
                occurrenceOn(base.plusWeeks(2))
        );
        when(visitOccurrenceRepository.findByUserIdAndVisitDateAfter(eq(USER_ID), any())).thenReturn(occurrences);

        RoutinePlace confirmed = RoutinePlace.builder()
                .userId(USER_ID)
                .latitude(LAT)
                .longitude(LNG)
                .status(RoutinePlaceStatus.CONFIRMED)
                .category("회사")
                .build();
        confirmed.applyManualSchedule("MONDAY,WEDNESDAY", LocalTime.of(9, 0), LocalTime.of(18, 0));
        when(routinePlaceRepository.findByUserId(USER_ID)).thenReturn(new java.util.ArrayList<>(List.of(confirmed)));

        detectionService.detectForUser(USER_ID);

        // 감지된 패턴(SATURDAY, 14~16시)이 아니라 사용자가 직접 넣은 값이 그대로 남아야 함
        assertThat(confirmed.getRoutineDays()).isEqualTo("MONDAY,WEDNESDAY");
        assertThat(confirmed.getRoutineTimeRangeStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(confirmed.getRoutineTimeRangeEnd()).isEqualTo(LocalTime.of(18, 0));
        assertThat(confirmed.getOccurrenceCount()).isEqualTo(3); // 통계(횟수)는 계속 갱신됨
    }
}
