package com.example.echo.routineplace.service;

import com.example.echo.location.dto.RawLocationData;
import com.example.echo.location.dto.RawVisitedPlace;
import com.example.echo.routineplace.entity.VisitOccurrence;
import com.example.echo.routineplace.repository.VisitOccurrenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 루틴 방문 장소 패턴 감지를 위한 원재료(VisitOccurrence) 기록 검증.
 *
 * 집으로 판정된 방문은 기록하지 않고(최소 수집 원칙), 같은 날 재호출 시 먼저 그날 기록을
 * 지운 뒤 다시 쌓아 멱등성을 보장하는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class VisitOccurrenceRecordingServiceTest {

    private static final Long USER_ID = 1L;
    private static final double HOME_LAT = 37.5665;
    private static final double HOME_LNG = 126.9780;
    private static final LocalDate VISIT_DATE = LocalDate.of(2026, 6, 1);

    @Mock
    private VisitOccurrenceRepository visitOccurrenceRepository;

    @InjectMocks
    private VisitOccurrenceRecordingService recordingService;

    private RawVisitedPlace place(double lat, double lng, int stayMinutes) {
        return RawVisitedPlace.builder()
                .latitude(lat)
                .longitude(lng)
                .visitStartTime(LocalTime.of(14, 0))
                .visitEndTime(LocalTime.of(15, 0))
                .stayDurationMinutes(stayMinutes)
                .build();
    }

    @Test
    @DisplayName("집으로 판정된 방문은 기록하지 않는다")
    void homeVisit_notRecorded() {
        RawLocationData raw = RawLocationData.builder()
                .visitedPlaces(List.of(place(HOME_LAT, HOME_LNG, 60)))
                .build();

        recordingService.recordOccurrences(USER_ID, raw, HOME_LAT, HOME_LNG, VISIT_DATE);

        verify(visitOccurrenceRepository, times(0)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("집이 아닌 방문만 기록하고, 재호출 시 그날 기록을 먼저 지운다(멱등성)")
    void nonHomeVisit_recordedIdempotently() {
        RawVisitedPlace outing = place(37.60, 127.10, 90);
        RawLocationData raw = RawLocationData.builder()
                .visitedPlaces(List.of(outing))
                .build();

        recordingService.recordOccurrences(USER_ID, raw, HOME_LAT, HOME_LNG, VISIT_DATE);

        verify(visitOccurrenceRepository, times(1)).deleteByUserIdAndVisitDate(USER_ID, VISIT_DATE);

        ArgumentCaptor<VisitOccurrence> captor = ArgumentCaptor.forClass(VisitOccurrence.class);
        verify(visitOccurrenceRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getLatitude()).isEqualTo(37.60);
        assertThat(captor.getValue().getVisitDate()).isEqualTo(VISIT_DATE);
        assertThat(captor.getValue().getDayOfWeek()).isEqualTo(VISIT_DATE.getDayOfWeek());
    }

    @Test
    @DisplayName("방문 장소가 없으면 저장 없이 그날 기록만 정리한다")
    void noVisitedPlaces_onlyClearsExistingRecordForTheDay() {
        RawLocationData raw = RawLocationData.builder().visitedPlaces(List.of()).build();

        recordingService.recordOccurrences(USER_ID, raw, HOME_LAT, HOME_LNG, VISIT_DATE);

        verify(visitOccurrenceRepository, times(1)).deleteByUserIdAndVisitDate(USER_ID, VISIT_DATE);
        verify(visitOccurrenceRepository, times(0)).save(org.mockito.ArgumentMatchers.any());
    }
}
