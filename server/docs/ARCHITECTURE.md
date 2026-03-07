# Echo 시스템 설계도

## 목차
1. [시스템 아키텍처](#1-시스템-아키텍처)
2. [대화 흐름 시퀀스](#2-대화-흐름-시퀀스)
3. [모듈 설계](#3-모듈-설계)
4. [데이터 모델](#4-데이터-모델)
5. [메모리 컨텍스트 구조](#5-메모리-컨텍스트-구조)
6. [외부 API 연동](#6-외부-api-연동)
7. [API 엔드포인트](#7-api-엔드포인트)

---

## 1. 시스템 아키텍처

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              클라이언트 (Android)                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ Health      │  │ 음성 녹음   │  │ 음성 재생   │  │ 대화 시간   │        │
│  │ Connect     │  │             │  │             │  │ 스케줄러    │        │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘        │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │ HTTP/REST
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Echo Server (Spring Boot)                          │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                      ConversationController                            │  │
│  │   /start    /message    /end    /tts-retry                            │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                      │                                       │
│  ┌───────────────────────────────────▼───────────────────────────────────┐  │
│  │                      ConversationService (오케스트레이터)               │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│       │              │              │              │              │          │
│       ▼              ▼              ▼              ▼              ▼          │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐          │
│  │Context  │  │Prompt   │  │AI       │  │Voice    │  │Diary    │          │
│  │Service  │  │Service  │  │Service  │  │Service  │  │Service  │          │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘          │
│       │              │              │              │              │          │
│       ▼              ▼              │              │              ▼          │
│  ┌─────────┐  ┌─────────┐          │              │         ┌─────────┐   │
│  │Health   │  │User     │          │              │         │Diary    │   │
│  │Service  │  │Service  │          │              │         │Repository│   │
│  └─────────┘  └─────────┘          │              │         └─────────┘   │
└──────│──────────────│──────────────│──────────────│─────────────│──────────┘
       │              │              │              │              │
       ▼              ▼              ▼              ▼              ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   MySQL     │  │ OpenWeather │  │  OpenAI     │  │  Azure TTS  │
│   (RDS)     │  │     API     │  │  GPT-4o     │  │  Clova TTS  │
│             │  │             │  │  Whisper    │  │             │
└─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘
```

### 핵심 설계 원칙

| 원칙 | 설명 |
|------|------|
| **오케스트레이터 패턴** | ConversationService가 모든 서비스 조율 |
| **세션 기반 컨텍스트** | ConcurrentHashMap으로 메모리 내 세션 관리 |
| **AI-First 대화** | AI가 먼저 인사하고 사용자가 응답 |
| **비동기 일기 생성** | 대화 종료 시 일기 생성 (사용자 대기 최소화) |

---

## 2. 대화 흐름 시퀀스

### 전체 대화 흐름

```
┌──────┐     ┌────────────┐     ┌─────────┐     ┌────────┐     ┌─────────┐     ┌───────┐
│Client│     │Conversation│     │Context  │     │Prompt  │     │AI       │     │Voice  │
│      │     │Controller  │     │Service  │     │Service │     │Service  │     │Service│
└──┬───┘     └─────┬──────┘     └────┬────┘     └───┬────┘     └────┬────┘     └───┬───┘
   │               │                 │              │               │              │
   │ POST /start   │                 │              │               │              │
   │ + HealthData  │                 │              │               │              │
   │──────────────>│                 │              │               │              │
   │               │ initContext()   │              │               │              │
   │               │────────────────>│              │               │              │
   │               │                 │──┐           │               │              │
   │               │                 │  │ Load User │               │              │
   │               │                 │  │ Preferences│              │              │
   │               │                 │  │ + Health   │              │              │
   │               │                 │<─┘           │               │              │
   │               │                 │              │               │              │
   │               │ buildSystemPrompt()            │               │              │
   │               │───────────────────────────────>│               │              │
   │               │                 │              │──┐            │              │
   │               │                 │              │  │ Template   │              │
   │               │                 │              │  │ + Variables│              │
   │               │                 │              │<─┘            │              │
   │               │                 │              │               │              │
   │               │ generateGreeting()             │               │              │
   │               │───────────────────────────────────────────────>│              │
   │               │                 │              │               │──┐           │
   │               │                 │              │               │  │ OpenAI    │
   │               │                 │              │               │  │ API Call  │
   │               │                 │              │               │<─┘           │
   │               │                 │              │               │              │
   │               │ textToSpeech()  │              │               │              │
   │               │─────────────────────────────────────────────────────────────>│
   │               │                 │              │               │              │──┐
   │               │                 │              │               │              │  │TTS
   │               │                 │              │               │              │<─┘
   │               │                 │              │               │              │
   │<──────────────│ Response (message + audio)     │               │              │
   │               │                 │              │               │              │
   │ POST /message │                 │              │               │              │
   │ + audio file  │                 │              │               │              │
   │──────────────>│                 │              │               │              │
   │               │ speechToText()  │              │               │              │
   │               │─────────────────────────────────────────────────────────────>│
   │               │                 │              │               │              │──┐
   │               │                 │              │               │              │  │STT
   │               │                 │              │               │              │<─┘
   │               │                 │              │               │              │
   │               │ generateResponse(systemPrompt, history, userMessage)         │
   │               │───────────────────────────────────────────────>│              │
   │               │                 │              │               │──┐           │
   │               │                 │              │               │  │ OpenAI    │
   │               │                 │              │               │<─┘           │
   │               │                 │              │               │              │
   │               │ textToSpeech()  │              │               │              │
   │               │─────────────────────────────────────────────────────────────>│
   │<──────────────│ Response (userMessage + aiResponse + audio)    │              │
   │               │                 │              │               │              │
   │ POST /end     │                 │              │               │              │
   │──────────────>│                 │              │               │              │
   │               │ generateDiary() │              │               │              │
   │               │────────────────────────────────────────────────> (async)      │
   │               │ finalizeContext()              │               │              │
   │               │────────────────>│              │               │              │
   │<──────────────│ Response (endedAt)             │               │              │
```

### 단계별 처리 내용

| 단계 | API | 처리 내용 |
|------|-----|----------|
| 1 | `/start` | 컨텍스트 초기화 → 시스템 프롬프트 생성 → AI 인사 → TTS |
| 2 | `/message` | STT → AI 응답 생성 → TTS → 히스토리 저장 |
| 3 | `/end` | 일기 생성 (비동기) → 컨텍스트 정리 |
| 4 | `/tts-retry` | 마지막 AI 응답 TTS 재생성 |

---

## 3. 모듈 설계

### 패키지 구조

```
com.example.echo
├── common                          # 공통 모듈
│   ├── auth
│   │   └── CurrentUser.java           # 사용자 인증 어노테이션
│   ├── config
│   │   ├── WebConfig.java             # 웹 설정
│   │   ├── CacheConfig.java           # 캐시 설정
│   │   └── OpenApiConfig.java         # Swagger 설정
│   ├── dto
│   │   └── WeatherData.java           # 날씨 데이터
│   ├── client
│   │   └── WeatherClient.java         # 날씨 API 클라이언트
│   └── exception
│       └── GlobalExceptionHandler.java
│
├── conversation                    # 대화 오케스트레이션 모듈
│   ├── controller
│   │   └── ConversationController.java
│   ├── service
│   │   └── ConversationService.java   # 핵심 오케스트레이터
│   ├── dto
│   │   ├── ConversationStartResponse.java
│   │   ├── ConversationResponse.java
│   │   ├── ConversationEndResponse.java
│   │   └── TtsRetryResponse.java
│   └── exception
│       └── ConversationNotFoundException.java
│
├── context                         # 세션 컨텍스트 모듈
│   ├── service
│   │   └── ContextService.java        # 컨텍스트 관리
│   └── domain
│       ├── UserContext.java           # 세션 데이터
│       └── ConversationTurn.java      # 대화 턴
│
├── ai                              # AI 응답 생성 모듈
│   ├── service
│   │   └── AIService.java             # OpenAI 호출
│   ├── client
│   │   └── OpenAIClient.java          # Feign 클라이언트
│   ├── dto
│   │   ├── ChatCompletionRequest.java
│   │   └── ChatCompletionResponse.java
│   └── exception
│       └── AIException.java
│
├── voice                           # 음성 처리 모듈
│   ├── service
│   │   ├── VoiceService.java          # 인터페이스
│   │   └── VoiceServiceImpl.java      # 구현체
│   ├── client
│   │   ├── STTClient.java             # Whisper API
│   │   └── TTSClient.java             # Azure/Clova TTS
│   └── dto
│       ├── SttResponse.java
│       └── TtsRequest.java
│
├── prompt                          # 프롬프트 관리 모듈
│   ├── service
│   │   └── PromptService.java         # 프롬프트 빌드
│   ├── entity
│   │   ├── PromptTemplate.java        # 템플릿 엔티티
│   │   └── PromptType.java            # SYSTEM, DIARY
│   └── repository
│       └── PromptTemplateRepository.java
│
├── health                          # 건강 데이터 모듈
│   ├── service
│   │   └── HealthDataService.java     # 건강 데이터 처리
│   ├── entity
│   │   └── HealthLog.java             # 건강 기록 엔티티
│   ├── repository
│   │   └── HealthLogRepository.java
│   └── dto
│       ├── HealthData.java            # 원시 데이터
│       └── EnrichedHealthData.java    # 분석 데이터
│
├── user                            # 사용자 모듈
│   ├── service
│   │   └── UserService.java
│   └── dto
│       ├── UserPreferences.java       # 사용자 선호도
│       └── VoiceSettings.java         # 음성 설정
│
└── diary                           # 일기 모듈
    ├── service
    │   └── DiaryService.java          # 일기 생성
    ├── entity
    │   └── Diary.java
    └── repository
        └── DiaryRepository.java
```

### 모듈별 책임

| 모듈 | 책임 | 주요 클래스 |
|------|------|------------|
| **conversation** | 대화 흐름 오케스트레이션 | `ConversationService` |
| **context** | 세션 컨텍스트 관리 | `ContextService`, `UserContext` |
| **ai** | OpenAI API 호출 | `AIService` |
| **voice** | STT/TTS 처리 | `VoiceServiceImpl` |
| **prompt** | 프롬프트 템플릿 관리 | `PromptService` |
| **health** | 건강 데이터 처리/분석 | `HealthDataService` |
| **user** | 사용자 정보/선호도 | `UserService` |
| **diary** | 일기 생성/저장 | `DiaryService` |

---

## 4. 데이터 모델

### ERD (Entity Relationship Diagram)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Database (MySQL)                            │
└─────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐       ┌──────────────────┐       ┌──────────────────┐
│      User        │       │   HealthLog      │       │     Diary        │
├──────────────────┤       ├──────────────────┤       ├──────────────────┤
│ PK id            │       │ PK id            │       │ PK id            │
│    name          │◄──┐   │ FK user_id       │───┐   │ FK user_id       │───┐
│    age           │   │   │    recorded_date │   │   │    diary_date    │   │
│    location      │   │   │    steps         │   │   │    content       │   │
│    birthday      │   └───│    sleep_minutes │   │   │    created_at    │   │
│    preferred_    │       │    sleep_start   │   │   └──────────────────┘   │
│      sleep_hours │       │    wake_up_time  │   │                          │
│    hobbies       │       │    exercise_km   │   │   ┌──────────────────┐   │
│    occupation    │       │    exercise_name │   │   │ PromptTemplate   │   │
│    family_info   │       │    created_at    │   │   ├──────────────────┤   │
│    voice_settings│       └──────────────────┘   │   │ PK id            │   │
│    created_at    │                              │   │    type          │   │
└──────────────────┘                              │   │    content       │   │
        │                                         │   │    is_active     │   │
        │                                         │   │    created_at    │   │
        └─────────────────────────────────────────┴───┴──────────────────┘
```

### 엔티티 상세

#### HealthLog
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| userId | Long | FK (User) |
| recordedDate | LocalDate | 기록 날짜 |
| steps | Integer | 걸음 수 |
| sleepDurationMinutes | Integer | 수면 시간 (분) |
| sleepStartTime | LocalTime | 취침 시간 |
| wakeUpTime | LocalTime | 기상 시간 |
| exerciseDistanceKm | Double | 운동 거리 |
| exerciseActivity | String | 운동 활동명 |

#### PromptTemplate
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| type | PromptType | SYSTEM, DIARY |
| content | String | 템플릿 내용 ({{변수}} 형식) |
| isActive | Boolean | 활성화 여부 |
| createdAt | LocalDateTime | 생성 시간 |

---

## 5. 메모리 컨텍스트 구조

### ContextService 구조

```
┌─────────────────────────────────────────────────────────────────────────┐
│              ContextService (ConcurrentHashMap<Long, UserContext>)       │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
            ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
            │ UserContext │ │ UserContext │ │ UserContext │
            │  userId: 1  │ │  userId: 2  │ │  userId: 3  │
            └─────────────┘ └─────────────┘ └─────────────┘
```

### UserContext 상세

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           UserContext                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  userId: Long                                                            │
│  date: LocalDate                                                         │
│  systemPrompt: String (캐싱됨)                                           │
│  lastAccessTime: LocalDateTime                                           │
│  isActive: boolean                                                       │
├─────────────────────────────────────────────────────────────────────────┤
│  conversationHistory: List<ConversationTurn>                             │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │ Turn 1: (null, "안녕하세요, 홍길동님!")           ← AI 첫인사    │    │
│  │ Turn 2: ("오늘 날씨 좋네요", "네, 산책하기 좋겠어요")             │    │
│  │ Turn 3: ("어제 손자가 왔어요", "손자분이 오셨군요!")              │    │
│  └─────────────────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────────────────┤
│  enrichedHealthData: EnrichedHealthData                                  │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │ steps: 5000              │ stepsFormatted: "5,000보"            │    │
│  │ sleepDurationMinutes: 420│ sleepDurationFormatted: "7시간"      │    │
│  │ avgSteps: 4500.0         │ stepsEvaluation: "평소보다 많음"      │    │
│  │ avgSleepHours: 6.5       │ sleepEvaluation: "적당"              │    │
│  └─────────────────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────────────────┤
│  preferences: UserPreferences                                            │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │ name: "홍길동"  │ age: 65  │ location: "서울"                   │    │
│  │ hobbies: "산책, 화초 가꾸기"  │ preferredSleepHours: 7          │    │
│  └─────────────────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────────────────┤
│  todayWeather: WeatherData                                               │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │ description: "맑음"  │ temperature: 18                          │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

### 컨텍스트 생명주기

| 단계 | 메서드 | 동작 |
|------|--------|------|
| 생성 | `initializeContext()` | User/Health/Weather 로드 → 컨텍스트 생성 |
| 조회 | `getContext()` | HashMap에서 조회, lastAccessTime 갱신 |
| 갱신 | `addConversationTurn()` | 대화 히스토리에 턴 추가 |
| 삭제 | `finalizeContext()` | HashMap에서 제거 |

---

## 6. 외부 API 연동

### 연동 구조

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Echo Server                                     │
└─────────────────────────────────────────────────────────────────────────┘
         │                    │                    │                    │
         │ OpenFeign          │ OpenFeign          │ OpenFeign          │ OpenFeign
         ▼                    ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│   OpenAI API    │  │   OpenAI API    │  │   Azure TTS     │  │ OpenWeatherMap  │
│   (Whisper)     │  │   (GPT-4o-mini) │  │   (or Clova)    │  │      API        │
├─────────────────┤  ├─────────────────┤  ├─────────────────┤  ├─────────────────┤
│ 용도: STT        │  │ 용도: 대화 생성 │  │ 용도: TTS        │  │ 용도: 날씨 조회 │
│ 입력: 음성파일   │  │ 입력: messages[] │  │ 입력: 텍스트     │  │ 입력: 위치      │
│ 출력: 텍스트     │  │ 출력: AI 응답   │  │ 출력: 음성데이터 │  │ 출력: 날씨정보  │
│                 │  │                 │  │                 │  │                 │
│ Model: whisper-1│  │ Model: gpt-4o-  │  │ Voice: 설정가능 │  │ 무료 플랜       │
│                 │  │         mini    │  │ Speed: 설정가능 │  │                 │
└─────────────────┘  └─────────────────┘  └─────────────────┘  └─────────────────┘
```

### API 상세

| API | 클라이언트 | 용도 | 모델/서비스 |
|-----|-----------|------|------------|
| **OpenAI Whisper** | `STTClient` | 음성→텍스트 | whisper-1 |
| **OpenAI Chat** | `OpenAIClient` | AI 응답 생성 | gpt-4o-mini |
| **Azure TTS** | `TTSClient` | 텍스트→음성 | Azure Cognitive Services |
| **OpenWeatherMap** | `WeatherClient` | 날씨 조회 | Current Weather API |

### OpenAI Messages 배열 구조

```json
{
  "model": "gpt-4o-mini",
  "messages": [
    {"role": "system", "content": "시스템 프롬프트"},
    {"role": "assistant", "content": "AI 첫 인사"},
    {"role": "user", "content": "사용자 첫 응답"},
    {"role": "assistant", "content": "AI 두번째 응답"},
    {"role": "user", "content": "현재 사용자 메시지"}
  ],
  "temperature": 0.7,
  "max_tokens": 1024
}
```

---

## 7. API 엔드포인트

### 엔드포인트 목록

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/conversations/start` | 대화 시작 |
| POST | `/api/conversations/message` | 음성 메시지 전송 |
| POST | `/api/conversations/end` | 대화 종료 |
| POST | `/api/conversations/tts-retry` | TTS 재시도 |

### 상세 스펙

#### POST /api/conversations/start

**Request Body** (optional):
```json
{
  "steps": 5000,
  "sleepDurationMinutes": 420,
  "sleepStartTime": "23:00:00",
  "wakeUpTime": "07:00:00",
  "exerciseDistanceKm": 2.5,
  "exerciseActivity": "산책"
}
```

**Response**:
```json
{
  "message": "안녕하세요, 홍길동님!",
  "audioData": "base64...",
  "timestamp": "2026-03-07T10:30:00"
}
```

#### POST /api/conversations/message

**Request**: `multipart/form-data`
- `audio`: 음성파일 (WAV, MP3, M4A)

**Response**:
```json
{
  "userMessage": "오늘 날씨가 좋네요",
  "aiResponse": "네, 산책하기 딱 좋겠어요!",
  "audioData": "base64...",
  "timestamp": "2026-03-07T10:31:00"
}
```

#### POST /api/conversations/end

**Response**:
```json
{
  "endedAt": "2026-03-07T11:00:00"
}
```

#### POST /api/conversations/tts-retry

**Response**:
```json
{
  "audioData": "base64..."
}
```

---

## 문서 정보

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs
- **Postman Collection**: `docs/Echo_API.postman_collection.json`
