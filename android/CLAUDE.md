# CLAUDE.md (echo-android)

This file provides guidance to Claude Code (claude.ai/code) when working with the echo-android project.

## 프로젝트 개요

경도인지장애 분들의 치매 예방을 위한 AI 음성 대화 시스템의 Android 클라이언트 앱입니다.

- **백엔드**: [echo](../server) 서버와 연동
- **핵심 기능**: AI 음성 대화, 일기 조회, 사용자 설정
- **데이터 수집**: Health Connect를 통한 Galaxy Watch 건강 데이터 (수면, 걸음수, 운동)
- **타겟 사용자**: 경도인지장애 어르신 (접근성 고려 필수)

## 빌드 및 실행 명령어

```bash
# 빌드
./gradlew build

# 디버그 APK 빌드
./gradlew assembleDebug

# 테스트 실행
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "com.example.echo.ClassName"

# 앱 설치 및 실행
./gradlew installDebug
```

## 기술 스택

- **언어**: Kotlin
- **UI**: Jetpack Compose + XML (혼용)
- **아키텍처**: Clean Architecture + MVVM
- **DI**: Hilt
- **네트워크**: Retrofit + OkHttp
- **직렬화**: Kotlinx Serialization
- **비동기**: Coroutines + Flow
- **건강 데이터**: Health Connect API
- **min SDK**: 26 (Android 8.0)
- **target SDK**: 34 (Android 14)

## 아키텍처

```
app/
├─ data/           # Repository 구현, API, Local DB
│  ├─ api/         # Retrofit 인터페이스 (ConversationApi, DiaryApi, UserApi)
│  ├─ model/       # DTO 클래스 (ConversationModels, DiaryModels, UserModels)
│  ├─ repository/  # Repository 구현체
│  └─ local/       # Room DB, DataStore
├─ domain/         # UseCase, Repository 인터페이스, Entity
│  ├─ model/       # 도메인 모델
│  ├─ repository/  # Repository 인터페이스
│  └─ usecase/     # 비즈니스 로직
├─ presentation/   # UI Layer (Compose + ViewModel)
│  ├─ ui/          # Composable 함수
│  ├─ viewmodel/   # ViewModel
│  ├─ model/       # UI 상태 모델 (ConversationState, ConversationUiState)
│  └─ navigation/  # Navigation 설정
└─ di/             # Hilt 모듈
```

## 주요 기능 및 화면

| 기능 | 설명 |
|------|------|
| 음성 대화 | 백엔드 `/api/conversations/*` 연동, 마이크/스피커 권한 필요 |
| 일기 조회 | 대화 요약 일기 목록/상세 |
| 사용자 설정 | 선호도, 정보 설정 |
| 알림 설정 | 대화 시간 알림 (AlarmManager) |
| 로그인 | JWT + 소셜 로그인 (Google/Kakao) - 예정 |

## 백엔드 API 연동

### Conversation API

```
POST /api/conversations/start     # 대화 시작 (HealthData 전송) → 음성(TTS) 응답
POST /api/conversations/message   # 음성 전송 → STT → AI → TTS 응답
POST /api/conversations/end       # 대화 종료 → 일기 생성
POST /api/conversations/tts-retry # TTS 재시도 → 음성 재생성
```

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

### Diary API (서버 구현 예정)

```
GET /api/diaries        # 일기 목록 조회
GET /api/diaries/{id}   # 일기 상세 조회
```

### User API (서버 구현 예정)

```
GET  /api/users/me              # 사용자 정보 조회
PUT  /api/users/me              # 사용자 정보 수정
GET  /api/users/me/preferences  # 사용자 설정 조회
PUT  /api/users/me/preferences  # 사용자 설정 수정
```

## Health Connect 연동

```kotlin
// 수집 데이터 타입
- SleepSessionRecord     // 수면 (시작/종료 시간, 총 시간)
- StepsRecord            // 걸음수
- ExerciseSessionRecord  // 운동 (거리, 활동명, 활동 목록)
```

**IMPORTANT**: min SDK 26이지만 Health Connect는 API 28+ 권장. 하위 버전 폴백 처리 필요

## 주의사항

- **접근성**: 어르신 대상 앱이므로 큰 글씨, 명확한 UI, 음성 안내 고려
- **권한**: 마이크, Health Connect, 알림 권한 런타임 요청 필요
- **인증**: 현재 백엔드는 MVP로 userId=1 고정, 추후 JWT 연동 예정
- **오프라인**: 네트워크 없을 시 에러 처리 필요 (대화 기능 불가)
- **TTS 실패 처리**: `/api/conversations/tts-retry` 엔드포인트로 TTS 재시도 가능

## 관련 문서

- **API 명세**: `docs/API.md`
- **Postman Collection**: `server/docs/Echo_API.postman_collection.json`
- **서버 문서**: `server/CLAUDE.md`
- **서버 아키텍처**: `server/docs/ARCHITECTURE.md`
