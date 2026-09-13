package com.example.echo.conversation.service;

import com.example.echo.routineplace.entity.RoutinePlace;
import com.example.echo.routineplace.entity.RoutinePlaceStatus;
import com.example.echo.routineplace.repository.RoutinePlaceRepository;
import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.user.repository.UserPreferencesRepository;
import com.example.echo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 경도인지장애 데모 페르소나 대화 시딩 - "체험 데이터 초기화"(ConversationDataResetService)의
 * 반대 방향 버튼. 실기기 데모 전에 사용자 정보·루틴 방문 장소를 한 번에 심어서 전체 파이프라인을
 * 바로 확인할 수 있게 한다. 개발/데모 편의용이며 재실행해도 안전하다(idempotent).
 *
 * 건강 데이터는 여기서 심지 않는다 - ConversationService.startConversation()이 대화 시작마다
 * 안드로이드가 그 순간 실제로 읽은 Health Connect 값으로 HealthLog를 무조건 덮어쓰고 그 값을
 * 그대로 프롬프트에 쓰므로, 서버에 미리 심어둬도 대화 시작과 동시에 사라져 아무 효과가 없다
 * (데모 중 건강 데이터 언급을 보여주려면 데모 모드를 별도로 만들어야 한다 - 후속 과제).
 *
 * "오늘의 방문 장소"(Room location_points)는 서버가 관여하지 않는 클라이언트 로컬 데이터라
 * 안드로이드 쪽에서 별도로 시딩한다 - ROUTINE_PLACE_LATITUDE/LONGITUDE와 반드시 동일한 좌표를 써야
 * 대화 중 루틴 장소로 인식된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationDemoSeedService {

    private static final LocalDate BIRTHDAY = LocalDate.of(1953, 4, 12);
    private static final String LOCATION = "서울특별시 강동구";
    private static final double HOME_LATITUDE = 37.5301;
    private static final double HOME_LONGITUDE = 127.1238;
    private static final String FAMILY_INFO = "딸 한 명과 매주 통화, 명절에 방문";
    private static final String OCCUPATION = "은퇴 (전직 초등학교 교사)";
    private static final String HOBBIES = "화초 가꾸기, 텔레비전 트로트 프로그램 시청";
    private static final String PREFERRED_TOPICS = "손녀 이야기, 옛날 학교 이야기, 날씨";
    private static final int PREFERRED_SLEEP_HOURS = 7;
    private static final LocalTime CONVERSATION_TIME = LocalTime.of(10, 0);

    /** 안드로이드 LocationStorageManager.DEMO_ROUTINE_PLACE_LAT/LNG와 반드시 동일해야 한다. */
    public static final double ROUTINE_PLACE_LATITUDE = 37.5415;
    public static final double ROUTINE_PLACE_LONGITUDE = 127.1352;
    private static final String ROUTINE_PLACE_CATEGORY = "복지관";
    /** DayOfWeek.name() 전체 요일명 컨벤션(RoutinePlaceDetectionService, RoutinePlaceService와 동일) - 약어(TUE 등)를 쓰면 한글 매핑 테이블에서 찾지 못해 영어로 노출된다. */
    private static final String ROUTINE_PLACE_DAYS = "TUESDAY,THURSDAY";
    private static final LocalTime ROUTINE_PLACE_START = LocalTime.of(14, 0);
    private static final LocalTime ROUTINE_PLACE_END = LocalTime.of(16, 0);
    private static final int ROUTINE_PLACE_OCCURRENCE_COUNT = 8;

    private final UserService userService;
    private final UserPreferencesRepository userPreferencesRepository;
    private final RoutinePlaceRepository routinePlaceRepository;

    @Transactional
    public void seed(Long userId) {
        seedPreferences(userId);
        seedRoutinePlace(userId);
        log.info("경도인지장애 데모 데이터 시딩 완료 - userId: {}", userId);
    }

    private void seedPreferences(Long userId) {
        UserPreferences request = UserPreferences.builder()
                .birthday(BIRTHDAY)
                .location(LOCATION)
                .familyInfo(FAMILY_INFO)
                .occupation(OCCUPATION)
                .hobbies(HOBBIES)
                .preferredTopics(PREFERRED_TOPICS)
                .voiceSettings(VoiceSettings.builder().build())
                .conversationTime(CONVERSATION_TIME)
                .preferredSleepHours(PREFERRED_SLEEP_HOURS)
                .build();
        userService.savePreferences(userId, request);
        userService.updateHomeLocation(userId, HOME_LATITUDE, HOME_LONGITUDE);

        com.example.echo.user.entity.UserPreferences prefs = userPreferencesRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("온보딩이 완료되지 않았습니다."));
        prefs.updateRoutinePlaceConsent(true);
    }

    private void seedRoutinePlace(Long userId) {
        routinePlaceRepository.deleteByUserId(userId);

        RoutinePlace place = RoutinePlace.builder()
                .userId(userId)
                .latitude(ROUTINE_PLACE_LATITUDE)
                .longitude(ROUTINE_PLACE_LONGITUDE)
                .status(RoutinePlaceStatus.CONFIRMED)
                .category(ROUTINE_PLACE_CATEGORY)
                .occurrenceCount(ROUTINE_PLACE_OCCURRENCE_COUNT)
                .lastDetectedAt(LocalDateTime.now())
                .confirmedAt(LocalDateTime.now())
                .build();
        place.applyManualSchedule(ROUTINE_PLACE_DAYS, ROUTINE_PLACE_START, ROUTINE_PLACE_END);
        routinePlaceRepository.save(place);
    }
}
