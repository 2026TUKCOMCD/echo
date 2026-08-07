# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

경도인지장애 분들의 치매 예방을 위한 AI 음성 대화 시스템 백엔드입니다.

- **핵심 기능**: 건강 데이터/날씨/사용자 선호도 기반 맞춤형 회상 대화 생성
- **부가 기능**: 하루 대화 내용을 일기 형식으로 생성 후 저장과 조회
- **대화 방식**: 음성 (STT/TTS)
- **대화 시간**: 사용자가 직접 설정하는 하루 한번 고정 시간
- **데이터 흐름**: 갤럭시 워치8 → Health Connect → 클라이언트 앱 → 서버
- **데이터 종류**: 건강 데이터 > (수면, 걸음 수, 운동 거리, 운동 활동 명) / 사용자가 거주하는 지역의 오늘 날씨 / 사용자 선호도 (취미, 직업, 가족 관계, 선호 주제) / 사용자 정보 (이름, 나이, 생일)
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
- **외부 API**: OpenRouter (채팅, 매일 다른 모델로 자동 로테이션), OpenAI (Whisper:STT), Azure Cognitive Services TTS
- **API 문서**: springdoc-openapi 2.3.0 (Swagger UI 적용, http://localhost:8080/swagger-ui.html)

## 아키텍처

### 핵심 데이터 흐름 (대화 처리)

```
ConversationController
├─ /api/conversations/start
│  └─ HealthDataService → ContextService → PromptService → AIService → VoiceService(TTS)
├─ /api/conversations/message
│  └─ VoiceService(STT) → AIService → VoiceService(TTS) → ContextService
├─ /api/conversations/end
│  └─ DiaryService(동기, 하루 1개 일기 생성/증분 갱신) → ContextService
│     └─ 응답에 diaryStatus(SUCCESS/FAILED/SKIPPED)·diaryId·diaryError 포함
└─ /api/conversations/tts-retry
   └─ VoiceService(TTS) - TTS 실패 시 재시도

DiaryController
├─ GET /api/diaries?days=N   → 최근 N일 일기 목록 (FAILED 기록 포함, 날짜 내림차순)
└─ GET /api/diaries/{id}     → 일기 단건 조회 (본인 소유만, 없으면 404)
```

### 일기 생성 방식 (증분 갱신)

- 대화 원문은 서버에 영구 저장되지 않으므로(클라이언트 로컬 Room에만 존재),
  대화 종료 시 **[기존 저장된 오늘 일기 + 이번 세션 대화]**를 DIARY 프롬프트(v3, `{{existingDiary}}` 변수)에 넣어 하루 전체 일기로 재생성
- 하루 1개 (`diaries` 테이블 `(user_id, diary_date)` unique), 날짜 경계는 Asia/Seoul 고정
- 생성 실패 시에도 FAILED 레코드를 남김 (기존 성공본 content는 보존, failureReason만 기록)
- 사용자 발화가 없는 세션(인사만 듣고 종료)은 스킵 (기존 일기 미변경)
- 대화 시작 시 최근 7일의 SUCCESS 일기를 시스템 프롬프트에 주입해 이전 대화를 기억하는 것처럼 이어감
  (`ConversationService.appendRecentDiaries`)

### 주요 모듈

| 패키지 | 역할                                                                       |
|--------|--------------------------------------------------------------------------|
| `conversation` | 대화 세션 오케스트레이션 (컨트롤러, 서비스)                                                |
| `voice` | STT/TTS 처리 (STT: OpenAI Whisper, TTS: Azure Cognitive Services) |
| `ai` | OpenRouter Chat Completion API 호출 (매일 자동으로 다른 모델 로테이션)                                              |
| `prompt` | 시스템 프롬프트 빌드 및 템플릿 관리 (Entity/Repository/캐싱)                                                              |
| `context` | 세션별 사용자 컨텍스트 관리 (ConcurrentHashMap 기반)                                   |
| `diary` | 대화 요약 및 일기 생성                                                            |
| `health` | 건강 데이터 처리 - `HealthData.java` 참조                                         |
| `user` | 사용자 정보 및 선호도                                                             |
| `common` | 예외 처리, 인증, 공통 설정                                                         |

## 핵심 파일

| 영역 | 파일 |
|------|------|
| 대화 흐름 | `conversation/service/ConversationService.java` |
| 컨텍스트 | `context/service/ContextService.java`, `context/domain/UserContext.java` |
| 음성 처리 | `voice/service/VoiceServiceImpl.java`, `voice/client/STTClient.java`, `voice/client/TTSClient.java` |
| AI 응답 | `ai/service/AIService.java`, `ai/client/OpenAIClient.java` |
| 건강 데이터 | `health/dto/HealthData.java`, `health/entity/HealthLog.java` |
| 사용자 정보 | `user/dto/UserPreferences.java`, `user/service/UserService.java` |
| 일기 | `diary/service/DiaryService.java`, `diary/entity/Diary.java`, `diary/repository/DiaryRepository.java`, `diary/controller/DiaryController.java` |
| 프롬프트 | `prompt/service/PromptService.java`, `prompt/entity/PromptTemplate.java`, `prompt/repository/PromptTemplateRepository.java` |

## 설정

- **application.yaml**: DB 연결, JPA 설정, Open AI API
- **OpenAI/OpenRouter API 키**: application-local.yaml 사용 ( 보안 목적 )

## 외부 API 연동

| API | 상태 | 클라이언트                           |
|-----|------|---------------------------------|
| STT | 구현 완료 | `STTClient` → OpenAI Whisper    |
| TTS | 구현 완료 | `TTSClient` → Azure Cognitive Services TTS |
| AI 응답 | 구현 완료 | `OpenRouterClient` → OpenRouter (매일 자동으로 다른 모델 로테이션, `ModelRotationService`) |
| 날씨 | 더미 구현 | `WeatherClient` → OpenWeatherMap |

## 주의사항

- **인증**: MVP 단계로 고정 userId=1 사용 (`CurrentUserArgumentResolver`)
- **세션 저장**: `ConcurrentHashMap` 사용, 서버 재시작 시 세션 손실
- **클라이언트**: 미정
- **심박수**: 수집하지 않음 (걸음수, 수면, 운동 거리, 운동 활동명만 사용)
- **프롬프트 템플릿**: Entity/Repository 구현 완료, `data.sql`에 초기 데이터 포함 (SYSTEM/CONVERSATION/DIARY 3종류)
- **UserService**: 현재 더미 데이터 반환 (User Entity/Repository 미구현, DB 연동 예정)
- **일기 날짜 기준**: 클라이언트가 일기↔로컬 대화 기록을 날짜(yyyy-MM-dd)로 매칭하므로, 일기 날짜는 항상 Asia/Seoul 기준으로 계산해야 함 (JVM 기본 타임존 사용 금지)
