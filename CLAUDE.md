# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

경도인지장애 분들의 치매 예방을 위한 AI 음성 대화 시스템 백엔드입니다.

- **핵심 기능**: 건강 데이터/날씨/사용자 선호도 기반 맞춤형 회상 대화 생성
- **부가 기능**: 하루 대화 내용을 일기 형식으로 생성 후 저장과 조회
- **대화 방식**: 음성 (STT/TTS)
- **대화 시간**: 사용자가 직접 설정하는 하루 한번 고정 시간
- **데이터 흐름**: 갤럭시 워치8 → Health Connect → 클라이언트 앱 → 서버
- **데이터 종류**: 건강 데이터 > (수면, 걸음 수, 운동 거리, 운동 활동 명) / 사용자가 거주하는 지역의 오늘 날씨 / 사용자 선호도 ( 취미, 직업, 가족 관계, 선호 주제) / 사용자 정보 ( 이름, 나이, 생일
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
- **외부 API**: OpenAI (GPT-4o-mini, Whisper:STT), Clova TTS API
- **API 문서**: 미구현

## 아키텍처

### 핵심 데이터 흐름 (대화 처리)

```
ConversationController
├─ /api/conversations/start
│  └─ ContextService → PromptService → AIService → VoiceService(TTS)
├─ /api/conversations/message
│  └─ VoiceService(STT) → PromptService → AIService → VoiceService(TTS)
└─ /api/conversations/end
   └─ DiaryService(비동기) → ContextService
```

### 주요 모듈

| 패키지 | 역할                                                                       |
|--------|--------------------------------------------------------------------------|
| `conversation` | 대화 세션 오케스트레이션 (컨트롤러, 서비스)                                                |
| `voice` | STT/TTS 처리 (STT:OpenFeign으로 Whisper API 호출 TTS: OpenFeign으로 clova api 호출) |
| `ai` | OpenAI API 호출                                              |
| `prompt` | 시스템 프롬프트 빌드                                                              |
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
| 음성 처리 | `voice/service/VoiceServiceImpl.java`, `voice/client/STTClient.java` |
| 건강 데이터 | `health/dto/HealthData.java` |
| 사용자 정보 | `user/dto/UserPreferences.java` |
| 일기 | `diary/service/DiaryService.java` |

## 설정

- **application.yaml**: DB 연결, JPA 설정, Open AI API
- **OpenAI API 키**: application-local.yaml 사용 ( 보안 목적 )

## 외부 API 연동

| API | 상태 | 클라이언트                           |
|-----|------|---------------------------------|
| STT | 구현 완료 | `STTClient` → OpenAI Whisper    |
| TTS | 미구현 | `TTSClient` → Clova API         |
| AI 응답 | 미구현 | `OpenAIClient` → OpenAI API     |
| 날씨 | 더미 구현 | `WeatherClient`→ OpenWeatherMap |

## 주의사항

- **인증**: MVP 단계로 고정 userId=1 사용 (`CurrentUserArgumentResolver`)
- **세션 저장**: `ConcurrentHashMap` 사용, 서버 재시작 시 세션 손실
- **클라이언트**: 미정
- **심박수**: 수집하지 않음 (걸음수, 수면, 운동 거리, 운동 활동명만 사용)
