# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

경도인지장애 분들의 치매 예방을 위한 AI 음성 대화 시스템 백엔드입니다.

- **핵심 기능**: 건강 데이터/날씨/위치 정보/사용자 선호도 기반 맞춤형 회상 대화 생성
- **부가 기능**: 하루 대화 내용을 일기 형식으로 생성 후 저장과 조회
- **대화 방식**: 음성 (STT/TTS)
- **대화 시간**: 사용자가 직접 설정하는 하루 한번 고정 시간
- **데이터 흐름**: 갤럭시 워치8 → Health Connect → 클라이언트 앱 → 서버
- **데이터 종류**: 건강 데이터 > (수면, 걸음 수, 운동 거리, 운동 활동 명) / 위치 데이터 > (현재 위치, 방문 장소, 체류 시간, 방문 시점 날씨) / 사용자가 거주하는 지역의 오늘 날씨 / 사용자 선호도 (취미, 직업, 가족 관계, 선호 주제) / 사용자 정보 (이름, 나이, 생일)
- **저장**: 사용자 정보/선호도/일기(DB), 대화 컨텍스트(메모리) , 프롬프트 템플릿( 대화, 시스템, 일기 3종류 ) (DB)

## 빌드 및 실행 명령어

```bash
# 빌드
./gradlew build

# 테스트 실행
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "com.example.echo.ClassName"

# 단일 테스트 메서드 실행
./gradlew test --tests "com.example.echo.ClassName.methodName"

# 애플리케이션 실행
./gradlew bootRun
```

## 기술 스택

- **프레임워크**: Spring Boot 3.5.9
- **언어**: Java 17
- **빌드 도구**: Gradle
- **데이터베이스**: MySQL 8.0 (개발), MySQL (운영/RDS)
- **ORM**: Spring Data JPA
- **HTTP 클라이언트**: Spring Cloud OpenFeign 2025.0.0
- **캐싱**: Caffeine (프롬프트 템플릿 캐싱)
- **외부 API**: OpenAI (GPT-4o-mini, Whisper:STT), Azure Cognitive Services TTS
- **API 문서**: springdoc-openapi 2.3.0 (Swagger UI 적용, http://localhost:8080/swagger-ui.html)

## 아키텍처

### 핵심 데이터 흐름 (대화 처리)

```
ConversationController
├─ /api/conversations/start
│  └─ HealthDataService → LocationService → ContextService → PromptService → AIService → VoiceService(TTS)
│     (LocationService: GeocodingClient + WeatherClient로 방문 장소 보강)
├─ /api/conversations/message
│  └─ VoiceService(STT) → AIService → VoiceService(TTS) → ContextService
├─ /api/conversations/end
│  └─ DiaryService(동기) → ContextService
└─ /api/conversations/tts-retry
   └─ VoiceService(TTS) - TTS 실패 시 재시도
```

### 주요 모듈

| 패키지 | 역할                                                                       |
|--------|--------------------------------------------------------------------------|
| `conversation` | 대화 세션 오케스트레이션 (컨트롤러, 서비스)                                                |
| `voice` | STT/TTS 처리 (STT: OpenAI Whisper, TTS: Azure Cognitive Services) |
| `ai` | OpenAI Chat Completion API 호출 (GPT-4o-mini)                                              |
| `prompt` | 시스템 프롬프트 빌드 및 템플릿 관리 (Entity/Repository/캐싱)                                                              |
| `context` | 세션별 사용자 컨텍스트 관리 (ConcurrentHashMap 기반)                                   |
| `diary` | 대화 요약 및 일기 생성                                                            |
| `health` | 건강 데이터 처리 - `HealthData.java` 참조                                         |
| `location` | 위치 데이터 처리 (역지오코딩, 방문 장소 보강) - `LocationService.java` 참조 |
| `user` | 사용자 정보 및 선호도                                                             |
| `common` | 예외 처리, 인증, 공통 설정, WeatherClient                                                         |

## 핵심 파일

| 영역 | 파일 |
|------|------|
| 대화 흐름 | `conversation/service/ConversationService.java` |
| 컨텍스트 | `context/service/ContextService.java`, `context/domain/UserContext.java` |
| 음성 처리 | `voice/service/VoiceServiceImpl.java`, `voice/client/STTClient.java`, `voice/client/TTSClient.java` |
| AI 응답 | `ai/service/AIService.java`, `ai/client/OpenAIClient.java` |
| 건강 데이터 | `health/dto/HealthData.java`, `health/entity/HealthLog.java` |
| 위치 데이터 | `location/service/LocationService.java`, `location/dto/RawLocationData.java`, `location/dto/LocationData.java` |
| 역지오코딩 | `location/client/GeocodingClient.java`, `location/service/GeocodingService.java` |
| 날씨 | `common/client/WeatherClient.java`, `common/dto/WeatherData.java`, `common/dto/VisitWeather.java` |
| 사용자 정보 | `user/dto/UserPreferences.java`, `user/service/UserService.java` |
| 일기 | `diary/service/DiaryService.java` |
| 프롬프트 | `prompt/service/PromptService.java`, `prompt/entity/PromptTemplate.java`, `prompt/repository/PromptTemplateRepository.java` |

## 프롬프트 템플릿

DB(`prompt_templates` 테이블)에 저장된 템플릿을 사용하며, `{{변수명}}` 형식의 플레이스홀더를 실제 값으로 치환합니다.

### 프롬프트 종류 (PromptType)

| 타입 | 용도 | 사용 시점 |
|------|------|----------|
| **SYSTEM** | AI 페르소나, 대화 규칙 정의 | 대화 시작 시 1회 생성 |
| **CONVERSATION** | (미사용) messages 배열로 대체 | - |
| **DIARY** | 대화 요약 → 일기 생성 | 대화 종료 시 |

### SYSTEM 프롬프트 변수 (v7)

| 카테고리 | 변수명 | 설명 |
|---------|--------|------|
| **사용자 정보** | `{{userName}}`, `{{userAge}}`, `{{userBirthday}}` | 이름, 나이, 생일 |
| **선호도** | `{{hobby}}`, `{{job}}`, `{{family}}`, `{{preferredTopics}}` | 취미, 직업, 가족, 선호 주제 |
| **날씨** | `{{weather}}`, `{{temperature}}` | 현재 날씨 설명, 기온 |
| **위치** | `{{currentCity}}`, `{{visitedPlacesText}}` | 현재 도시, 방문 장소 목록 |
| **건강** | `{{steps}}`, `{{exerciseDistance}}`, `{{exerciseActivity}}`, `{{activityList}}` | 걸음수, 운동 거리, 활동명, 활동 목록 |
| **수면** | `{{sleepDuration}}`, `{{sleepStartTime}}`, `{{wakeUpTime}}`, `{{preferredSleepHours}}` | 수면 시간, 취침/기상 시간, 목표 수면 |
| **평가** | `{{sleepEvaluation}}`, `{{stepsEvaluation}}`, `{{wakeTimeEvaluation}}` | 수면/걸음수/기상 평가 |

### 템플릿 버전 관리

- `data.sql`에 초기 템플릿 포함 (현재 v7 활성화)
- `is_active = true`인 최신 템플릿만 사용
- 캐싱 적용 (`@Cacheable`)으로 DB 접근 최소화

## 설정

- **application.yaml**: DB 연결, JPA 설정, 외부 API URL
- **application-local.yaml**: API 키 설정 (보안 목적, git 제외)
  - `openai.api.key`: OpenAI API 키
  - `azure.tts.api-key`: Azure TTS API 키
  - `kakao.api.key`: Kakao REST API 키 (역지오코딩)
  - `weather.api.key`: OpenWeatherMap API 키

## 외부 API 연동

| API | 상태 | 클라이언트                           |
|-----|------|---------------------------------|
| STT | 구현 완료 | `STTClient` → OpenAI Whisper    |
| TTS | 구현 완료 | `TTSClient` → Azure Cognitive Services TTS |
| AI 응답 | 구현 완료 | `OpenAIClient` → OpenAI GPT-4o-mini |
| 날씨 | 구현 완료 | `WeatherClient` → OpenWeatherMap (현재 날씨 + Timemachine API) |
| 역지오코딩 | 구현 완료 | `GeocodingClient` → Kakao Local API |

## 주의사항

- **인증**: MVP 단계로 고정 userId=1 사용 (`CurrentUserArgumentResolver`)
- **세션 저장**: `ConcurrentHashMap` 사용, 서버 재시작 시 세션 손실
- **클라이언트**: 미정
- **심박수**: 수집하지 않음 (걸음수, 수면, 운동 거리, 운동 활동명만 사용)
- **프롬프트 템플릿**: Entity/Repository 구현 완료, `data.sql`에 초기 데이터 포함 (SYSTEM/CONVERSATION/DIARY 3종류)
- **UserService**: 현재 더미 데이터 반환 (User Entity/Repository 미구현, DB 연동 예정)
- **DiaryService**: 현재 TODO 상태 (Diary Entity/Repository 미구현, 일기 생성 로직 구현 예정)
