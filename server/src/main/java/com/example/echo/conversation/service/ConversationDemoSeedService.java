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
import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.user.repository.UserPreferencesRepository;
import com.example.echo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 경도인지장애 데모 페르소나 대화 시딩 - "체험 데이터 초기화"(ConversationDataResetService)의
 * 반대 방향 버튼. 실기기 데모 전에 사용자 정보·루틴 방문 장소·최근 일기·장기기억을 한 번에 심어서
 * 전체 파이프라인을 바로 확인할 수 있게 한다. 개발/데모 편의용이며 재실행해도 안전하다(idempotent).
 *
 * 최근 일기(D-3~D-1)와 장기기억은 "오늘 데모 대화를 시작하기 전부터 이미 며칠간 대화해온 사용자"처럼
 * 보이게 하려는 목적이다 - ConversationService.appendRecentDiaries()가 최근 7일 SUCCESS 일기를,
 * PromptService가 장기기억을 시스템 프롬프트에 주입하므로 이게 비어 있으면 데모 첫 대화에서 회상
 * 기능이 전혀 드러나지 않는다. 오늘 날짜 일기는 일부러 심지 않는다 - 데모 중 실제 대화가 그 날의
 * 일기를 처음부터 생성하도록 비워둔다.
 *
 * 건강 데이터는 여기서 심지 않는다 - ConversationService.startConversation()이 대화 시작마다
 * 안드로이드가 그 순간 실제로 읽은 Health Connect 값으로 HealthLog를 무조건 덮어쓰고 그 값을
 * 그대로 프롬프트에 쓰므로, 서버에 미리 심어둬도 대화 시작과 동시에 사라져 아무 효과가 없다
 * (데모 중 건강 데이터 언급을 보여주려면 데모 모드를 별도로 만들어야 한다 - 후속 과제).
 *
 * "오늘의 방문 장소"(Room location_points)는 서버가 관여하지 않는 클라이언트 로컬 데이터라
 * 안드로이드 쪽에서 별도로 시딩한다 - ROUTINE_PLACE_LATITUDE/LONGITUDE와 반드시 동일한 좌표를 써야
 * 대화 중 루틴 장소로 인식된다.
 *
 * 진행 중인 대화 세션도 함께 종료한다(ContextService.finalizeContext) - 전시에서 관람자마다
 * 새 세션을 보장하기 위함이다. 이전 관람자가 대화를 끝까지 마치지 않고 자리를 떠 서버에 세션이
 * 남아있어도, 다음 관람자를 위해 이 버튼만 누르면 항상 깨끗한 상태에서 새 대화가 시작된다.
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
    private static final String PREFERRED_TOPICS = "손녀 이야기, 복지관 모임 이야기, 날씨";
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

    private static final DateTimeFormatter DIARY_TITLE_FORMATTER = DateTimeFormatter.ofPattern("M월 d일의 일기");

    private final UserService userService;
    private final UserPreferencesRepository userPreferencesRepository;
    private final RoutinePlaceRepository routinePlaceRepository;
    private final DiaryRepository diaryRepository;
    private final MemoryRepository memoryRepository;
    private final ContextService contextService;
    private final Clock clock;

    @Transactional
    public void seed(Long userId) {
        contextService.finalizeContext(userId);
        seedPreferences(userId);
        seedRoutinePlace(userId);
        seedDiaries(userId);
        seedMemories(userId);
        log.info("경도인지장애 데모 데이터 시딩 완료 - userId: {}", userId);
    }

    private void seedPreferences(Long userId) {
        UserPreferences request = UserPreferences.builder()
                .birthday(BIRTHDAY)
                .location(LOCATION)
                .familyInfo(FAMILY_INFO)
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

    /**
     * 최근 3일치(D-3~D-1) 일기를 심는다. 오늘 날짜는 제외한다 - 데모 대화가 그 날의 일기를
     * 처음부터(existingDiary=null) 증분 생성하도록 비워두기 위함이다.
     */
    private void seedDiaries(Long userId) {
        diaryRepository.deleteByUserId(userId);
        // deleteByUserId는 파생 삭제라 flush 전까지 DELETE가 지연되는데, Hibernate는 같은 flush 안에서
        // INSERT를 DELETE보다 먼저 실행한다. flush 없이 바로 아래에서 새 일기를 저장하면 오늘 이전에
        // 실제로 쌓인 일기와 날짜가 겹칠 때 uq_diaries_user_date 유니크 제약을 위반한다(운영에서 확인된 버그).
        diaryRepository.flush();
        LocalDate today = LocalDate.now(clock);

        saveDiary(userId, today.minusDays(3),
                "딸이 전화를 걸어와 손녀 이야기를 한참 들었다. 다음 주에 놀러 오겠다는 말에 기분이 좋았다.",
                "포근함", "맑음");
        saveDiary(userId, today.minusDays(2),
                "화요일이라 동네 복지관에 다녀왔다. 아는 사람들과 이야기를 나누다 보니 시간 가는 줄 몰랐다.",
                "즐거움", "흐림");
        saveDiary(userId, today.minusDays(1),
                "손녀가 사진을 보내줘서 한참을 들여다봤다. 목소리도 듣고 싶어 딸에게 전화를 걸었다.",
                "그리움", "맑음");
    }

    private void saveDiary(Long userId, LocalDate diaryDate, String content, String mood, String weather) {
        Diary diary = Diary.builder()
                .userId(userId)
                .diaryDate(diaryDate)
                .title(diaryDate.format(DIARY_TITLE_FORMATTER))
                .content(content)
                .mood(mood)
                .weather(weather)
                .status(DiaryStatus.SUCCESS)
                .build();
        diaryRepository.save(diary);
    }

    /**
     * 장기기억을 심는다. 관람자가 즉흥적으로도 답하기 쉽도록 가족·루틴 두 화제로만 구성한다
     * (직업/취미/학창시절처럼 구체적인 과거사를 "기억해서 답변"해야 하는 화제는 제외) -
     * 위 시딩 필드(가족·루틴 장소)와 어긋나지 않는 내용으로 구성해 회상 대화 중 프롬프트에 함께
     * 주입되는 사용자 선호도·루틴 장소와 모순이 생기지 않도록 한다.
     */
    private void seedMemories(Long userId) {
        memoryRepository.deleteByUserId(userId);
        memoryRepository.saveAll(List.of(
                Memory.builder().userId(userId).lifePeriod("최근").topic("가족")
                        .content("딸이 매주 전화를 걸어 안부를 묻고, 명절마다 손녀를 데리고 집에 방문한다.")
                        .tags("딸,손녀,전화,명절").build(),
                Memory.builder().userId(userId).lifePeriod("최근").topic("습관")
                        .content("화요일과 목요일 오후에는 동네 복지관에 나가 사람들과 시간을 보낸다.")
                        .tags("복지관,루틴,사교").build()
        ));
    }
}
