# CLAUDE.md (echo-android)

This file provides guidance to Claude Code (claude.ai/code) when working with the echo-android project.

## 프로젝트 개요

경도인지장애 분들의 치매 예방을 위한 AI 음성 대화 시스템의 Android 클라이언트 앱입니다.

- **백엔드**: [echo](../server) 서버와 연동
- **핵심 기능**: AI 음성 대화, 일기 조회, 사용자 설정
- **데이터 수집**: Health Connect를 통한 Galaxy Watch 건강 데이터 (수면, 걸음수, 운동), 앱 자체 GPS 수집으로 방문 장소 추출
- **타겟 사용자**: 경도인지장애 어르신 (접근성 고려 필수)

## 빌드 및 실행 명령어

서버 주소별 product flavor(`local`, `prod`)가 있어서 변형(variant) 단위 태스크는 flavor 이름을 붙여야 한다.
`installDebug`, `compileDebugKotlin`처럼 flavor가 없는 이름은 모호해서 실패하고, `./gradlew test --tests`는 지원되지 않는다.

```bash
# 빌드 (모든 변형의 컴파일 + lint + 단위 테스트)
./gradlew build

# 디버그 APK 빌드 (local·prod 둘 다)
./gradlew assembleDebug

# 단위 테스트 (local 디버그 변형)
./gradlew testLocalDebugUnitTest

# 단일 테스트 클래스 실행
./gradlew testLocalDebugUnitTest --tests "com.example.graduation_project.ClassName"

# 앱 설치
./gradlew installLocalDebug   # 로컬 서버 (USB 연결 + adb reverse tcp:8080 tcp:8080)
./gradlew installProdDebug    # 운영 서버

# 디버그 HTTP 로그에 요청·응답 본문까지 남기기 (기본은 헤더까지만 - 본문에 개인정보가 있음, 인증 본문은 켜도 제외)
./gradlew installLocalDebug -Pecho.logHttpBody=true
```

## 기술 스택

- **언어**: Kotlin
- **UI**: Jetpack Compose
- **아키텍처**: MVVM (data / domain / presentation 계층)
- **DI**: 사용하지 않음 — ViewModel·Repository는 의존성을 생성자 파라미터 + 기본값으로 받는다 (테스트에서 가짜 객체 주입)
- **네트워크**: Retrofit + OkHttp (실시간 메시지는 OkHttp WebSocket)
- **직렬화**: Kotlinx Serialization
- **비동기**: Coroutines + Flow
- **로컬 저장소**: Room (SQLCipher 암호화, DB v5), EncryptedSharedPreferences (토큰)
- **음성**: Silero VAD(android-vad), MediaPlayer
- **건강 데이터**: Health Connect API
- **min SDK**: 28 (Android 9)
- **target/compile SDK**: 36

## 아키텍처

```
app/src/main/java/com/example/graduation_project/
├─ data/
│  ├─ api/         # Retrofit 인터페이스, ApiClient, 인증(AuthInterceptor·TokenAuthenticator),
│  │               # 스트리밍 프레임(ConversationStreamProtocol), 실시간 WebSocket(LiveMessageSession)
│  ├─ model/       # DTO 클래스 (ConversationModels, DiaryModels, UserModels 등)
│  ├─ repository/  # Repository 구현체
│  ├─ local/       # Room DB(AppDatabase·dao·entity), TokenStorage, DisplaySettingsStorage
│  ├─ voice/       # 녹음·VAD·재생 (VoiceRecordingManager, AudioPlayerManager)
│  ├─ health/      # Health Connect
│  ├─ location/    # GPS 수집 서비스·스케줄러
│  ├─ alarm/       # 대화 시간 알림
│  └─ receiver/    # BootReceiver
├─ domain/         # 도메인 모델·인터페이스·UseCase (health, location, voice, permission)
├─ presentation/   # 화면별 패키지 (Compose 화면 + ViewModel)
│  │               # conversation, diary, history, home, settings, auth, onboarding, permission, health
│  ├─ model/       # UI 상태 모델 (ConversationState, ConversationUiState)
│  └─ common/ · component/ · character/   # 공용 UI
├─ ui/theme/       # 테마·색·글자 크기
└─ util/
```

네비게이션 라우트는 `MainActivity`의 `Routes`와 하단 탭(`presentation/common/EchoTabBar`)에 정의되어 있다.

## 주요 기능 및 화면

| 기능 | 설명 |
|------|------|
| 음성 대화 | 백엔드 `/api/conversations/*` 연동, 마이크/스피커 권한 필요 |
| 일기 탭 | 하단 탭(라우트 "history" 유지, 라벨 "일기"). 서버 일기(Room `diaries` 캐시) + 일기 없는 날의 로컬 대화 세션을 날짜(Asia/Seoul "yyyy-MM-dd") 기준으로 병합 표시. `presentation/diary/` 참조 |
| 일기 상세 | `diary_detail/{date}` 라우트. 일기 본문 + 그날의 대화 세션 목록 → 세션 탭 시 `history_detail/{conversationId}` 말풍선 화면 재사용 |
| 사용자 설정 | 선호도, 정보 설정 |
| 알림 설정 | 대화 시간 알림 (AlarmManager) |
| 로그인 | 아이디·비밀번호 + JWT. 소셜 로그인(Google/Kakao)은 미구현 |

## 백엔드 API 연동

### Conversation API

```
POST /api/conversations/start          # 대화 시작 (HealthData·위치 전송) → 음성(TTS) 응답
POST /api/conversations/start-stream   # 대화 시작 스트리밍 버전 (프레임 스트림)
POST /api/conversations/message        # 음성 전송 → STT → AI → TTS 응답
POST /api/conversations/message-stream # 메시지 스트리밍 버전 (프레임 스트림)
WS   /api/conversations/message-live   # 말하는 동안 실시간 전사 (WebSocket, LiveMessageSession)
POST /api/conversations/end            # 대화 종료 → 일기 생성
POST /api/conversations/tts-retry      # TTS 재시도 → 음성 재생성
```

- 프레임 규격·실패 규칙은 `docs/API.md`(1-1, 2-1, 2-2절)와 `ConversationStreamProtocol` 참고
- 스트리밍 엔드포인트가 **404/405일 때만** 비스트리밍 엔드포인트로 폴백한다 (`ConversationRepository`). 그 외 실패에서 다시 보내면 서버 히스토리가 중복된다
- **스트리밍 엔드포인트를 추가하면 `ApiClient.streamingPaths`에도 넣어야 한다** — 본문 로깅(`-Pecho.logHttpBody=true`)이 응답을 끝까지 버퍼링해 스트리밍을 무력화함

### HealthData 요청 필드 (대화 시작 시)

```kotlin
data class HealthData(
    val sleepDurationMinutes: Int?,   // 수면 시간 (분)
    val sleepStartTime: String?,      // "HH:mm" 형식
    val wakeUpTime: String?,          // "HH:mm" 형식
    val steps: Int?,                  // 걸음 수
    val exerciseDistanceKm: Double?,  // 운동 거리 (km)
    val exerciseActivity: String?,    // 운동 활동명
    val activityList: String?         // 오늘 운동 활동 목록
)
```

### Diary API

```
GET /api/diaries?days=30                          # 최근 N일 일기 목록 (FAILED 실패 기록 포함, 날짜 내림차순)
GET /api/diaries?startDate=yyyy-MM-dd&endDate=... # 날짜 범위 일기 목록 (캘린더 월 조회용)
GET /api/diaries/{id}                             # 일기 단건 조회
```

- 일기는 대화 종료(`POST /api/conversations/end`) 시 서버가 자동 생성 (하루 1개, 증분 갱신)
- `/end` 응답의 `diaryStatus`(SUCCESS/FAILED/SKIPPED)·`diaryError`로 생성 결과 확인 (실패 시 Toast+로그로 명시)
- 클라이언트는 응답을 Room `diaries` 테이블(날짜 PK)에 캐시 → 오프라인 열람 지원 (`DiaryRepository`)

### Auth / User API

```
POST /api/auth/signup | login | refresh | logout   # 회원가입·로그인·토큰 갱신·로그아웃
GET  /api/users/me                                 # 사용자 정보 조회
PUT  /api/users/me                                 # 사용자 정보 수정
GET  /api/users/me/preferences                     # 사용자 설정 조회
PUT  /api/users/me/preferences/{항목}               # 항목별 수정 (name, birthday, voice-settings, conversation-time 등)
GET  /api/users/me/onboarding-status               # 온보딩 완료 여부
```

그 밖에 `GET /api/weather`(홈 날씨), `/api/users/me/routine-places/*`(자주 가는 장소)를 사용한다.

## Health Connect 연동

```kotlin
// 수집 데이터 타입
- SleepSessionRecord     // 수면 (시작/종료 시간, 총 시간)
- StepsRecord            // 걸음수
- ExerciseSessionRecord  // 운동 (활동명, 활동 목록)
- DistanceRecord         // 운동 거리 (없으면 운동 세션의 laps로 폴백)
```

기기에 Health Connect가 없거나 업데이트가 필요할 수 있으므로 `HealthConnectAvailability`로 상태를 확인한 뒤 사용한다.

## 주의사항

- **접근성**: 어르신 대상 앱이므로 큰 글씨, 명확한 UI, 음성 안내 고려
- **권한**: 마이크, Health Connect, 위치, 알림 권한 런타임 요청 필요
- **인증**: JWT(`Authorization: Bearer`). 토큰은 `TokenStorage`(EncryptedSharedPreferences)에 저장하고 401이면 `TokenAuthenticator`가 refresh 토큰으로 갱신한다. 로컬 캐시(Room)는 계정(userId)별로 격리
- **로컬 DB**: SQLCipher로 암호화되어 있어 `adb shell sqlite3`로 직접 열 수 없다
- **오프라인**: 네트워크 없을 시 에러 처리 필요 (대화 기능 불가)
- **TTS 실패 처리**: `/api/conversations/tts-retry` 엔드포인트로 TTS 재시도 가능

## 관련 문서

- **API 명세**: `docs/API.md`
- **Postman Collection**: `server/docs/Echo_API.postman_collection.json`
- **서버 문서**: `server/CLAUDE.md`
- **서버 아키텍처**: `server/docs/ARCHITECTURE.md`
