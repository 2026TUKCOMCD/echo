# 🧠 치매 예방 회상 대화 시스템 - 9주 로드맵

> **👥 팀원 3명 | 📅 9주 개발 | ⏱️ 총 810시간 | 🔄 2주 스프린트**


## 📊 에픽 개요

| Epic | 이름 | 우선순위 | 담당 | SP | 스프린트 |
|:---:|:---|:---:|:---:|---:|:---:|
| E1 | 사용자 인증 및 온보딩 | 🔵 P2 | Fullstack | 13 | Sprint 5 (선택) |
| E2 | 음성 대화 시스템 (앱) | 🔴 P0 | Android | 21 | Sprint 2 |
| E3 | 서버 AI 대화 생성 | 🔴 P0 | Server | 34 | Sprint 1 |
| E4 | 건강 데이터 연동 | 🟡 P1 | App+Server | 13 | Sprint 3 |
| E5 | 위치 기반 대화 | 🟡 P1 | App+Server | 21 | Sprint 3 |
| E6 | 일기 요약 기능 | 🟡 P1 | Server+App | 13 | Sprint 4 |
| E7 | 사용자 설정 | 🟡 P1 | App+Server | 8 | Sprint 5 |
| E8 | 알림 시스템 | 🔵 P2 | App | 5 | - |
| E9 | 데이터 관리 및 보안 | 🟡 P1 | App+Server | 8 | Sprint 4 |
| E10 | 에러 처리 및 UX 개선 | 🔵 P2 | App | 8 | - |
| | | | **총합** | **144** | |

---

## 📅 주차별 스프린트 계획

---

### W1 | 🟣 준비: 환경 설정 & 기초

> **목표**: 개발 환경 구축 및 기술 학습

| 태스크 | 담당 |
|:---|:---:|
| Spring Boot 프로젝트 생성 | Server |
| Android 프로젝트 생성 | App |
| AWS RDS 설정 & 고정 사용자 DB | Server |
| OpenAI API 연동 테스트 | Server |
| Health Connect 기술 학습 | App |

**📦 산출물**: 프로젝트 뼈대, OpenAI 연동 테스트 완료

---

### W2-3 | Sprint 1: 서버 AI 대화 시스템

> **목표**: AI 대화 생성 서버 완성 (전원 서버 집중)  
> **Sprint SP**: 34

#### 🔴 E3 서버 AI 대화 생성 `34 SP`

| User Story | 설명 | SP | 주요 Task |
|:---|:---|---:|:---|
| US 3.1 | 대화 세션 시작 | 5 | SessionContextService, 세션 데이터 구조, 첫 인사 생성 |
| US 3.2 | 사용자 음성 처리 (STT) | 5 | 음성 파일 업로드 API, VoiceService, OpenAI Whisper 연동 |
| US 3.3 | 프롬프트 생성 | 8 | PromptService, 페르소나 템플릿, DataIntegrationService 연동 |
| US 3.4 | AI 응답 생성 | 8 | AIService, OpenAI Chat Completion, 컨텐츠 필터링 |
| US 3.5 | 음성 변환 (TTS) 및 전송 | 5 | TTS Client, 음성 파일 저장, 속도/톤 파라미터 |
| US 3.6 | 대화 종료 판단 | 3 | 턴 카운트 체크, 종료 신호 감지, 마무리 인사 |
| | **소계** | **34** | |

**📦 데모**: Postman으로 전체 대화 API 테스트 성공

---

### W4-5 | Sprint 2: 앱 음성 대화

> **목표**: 음성 대화 앱 완성 (전원 앱 집중)  
> **Sprint SP**: 21

#### 🔴 E2 음성 대화 시스템 (앱) `21 SP`

| User Story | 설명 | SP | 주요 Task |
|:---|:---|---:|:---|
| US 2.1 | 대화 시작하기 | 3 | MainActivity UI, ConversationViewModel, RecyclerView 어댑터 |
| US 2.2 | 음성 녹음 및 전송 | 5 | AudioRecordManager, 마이크 권한, FileUploader (Multipart) |
| US 2.3 | AI 응답 듣기 | 5 | AudioPlayerManager, 음성 파일 다운로드, 재생 속도 조절 |
| US 2.4 | 대화 기록 보기 | 3 | Message Entity, Room Database, MessageDao |
| US 2.5 | 대화 종료 알림 받기 | 2 | 종료 API 응답 처리, 종료 다이얼로그 UI |
| US 2.6 | 대화 흐름 제어 | 3 | ConversationState enum, 상태 전환 로직, 에러 처리 |
| | **소계** | **21** | |

**📦 데모**: 실제 음성으로 AI와 대화 가능 (MVP 완성 🎉)

---

### W6 | Sprint 3: 맥락 데이터 연동

> **목표**: 건강/위치 데이터 기반 맞춤형 대화  
> **Sprint SP**: 34 (E4: 13 + E5: 21)

#### 🟡 E4 건강 데이터 연동 `13 SP`

| User Story | 설명 | SP | 주요 Task |
|:---|:---|---:|:---|
| US 4.1 | Health Connect 권한 요청 | 2 | 라이브러리 추가, 권한 요청 다이얼로그, HealthViewModel |
| US 4.2 | 건강 데이터 수집 | 5 | HealthDataManager, 걸음수/심박수/수면 조회 |
| US 4.3 | 건강 데이터 서버 전송 | 3 | HealthRepository, 배치 업로드, WorkManager 재시도 |
| US 4.4 | 건강 데이터 기반 대화 | 3 | HealthData Entity, 프롬프트에 건강 데이터 통합 |
| | **소계** | **13** | |

#### 🟡 E5 위치 기반 대화 `21 SP`

| User Story | 설명 | SP | 주요 Task |
|:---|:---|---:|:---|
| US 5.1 | 위치 권한 요청 | 2 | 위치 권한 설정, LocationManager |
| US 5.2 | 현재 위치 조회 | 3 | FusedLocationProvider, getCurrentLocation() |
| US 5.3 | 역지오코딩 (좌표→주소) | 3 | ReverseGeoClient, Kakao Map API 연동 |
| US 5.4 | 날씨 정보 조회 | 2 | WeatherClient, OpenWeather API 연동 |
| US 5.5 | 걷기 운동 GPS 경로 수집 | 5 | ExerciseSessionRecord 조회, StayPointDetector |
| US 5.6 | 방문 장소 서버 전송 | 3 | VisitedPlace DTO, LocationData Entity |
| US 5.7 | 방문 장소 기반 대화 | 3 | DataIntegrationService, 프롬프트 템플릿 |
| | **소계** | **21** | |

**📦 데모**: "오늘 공지천 다녀오셨네요? 3000보나 걸으셨어요!"

---

### W7 | Sprint 4: 요약 & 데이터 관리

> **목표**: 일기 기능 및 데이터 보안  
> **Sprint SP**: 21 (E6: 13 + E9: 8)

#### 🟡 E6 일기 요약 기능 `13 SP`

| User Story | 설명 | SP | 주요 Task |
|:---|:---|---:|:---|
| US 6.1 | 대화 요약 생성 | 5 | SummaryService, 요약 프롬프트 템플릿, 감정 분석 |
| US 6.2 | 요약 저장 (RDS) | 3 | DailySummary Entity, DailySummaryRepository |
| US 6.3 | 요약 앱 전송 | 2 | 요약 조회 API, DiaryRepository |
| US 6.4 | 일기 화면 조회 | 3 | DiaryActivity UI, DiaryViewModel, 날짜 필터 |
| | **소계** | **13** | |

#### 🟡 E9 데이터 관리 및 보안 `8 SP`

| User Story | 설명 | SP | 주요 Task |
|:---|:---|---:|:---|
| US 9.1 | 토큰 관리 | 3 | TokenStorage (Encrypted), TokenInterceptor, 토큰 갱신 |
| US 9.2 | 로컬 데이터베이스 | 3 | AppDatabase, DiaryEntity, MessageEntity, DAO |
| US 9.3 | 캐시 관리 | 2 | 캐시 자동 삭제 (WorkManager), 크기 제한 |
| | **소계** | **8** | |

**📦 데모**: 일기 화면에서 대화 요약 조회

---

### W8 | Sprint 5: 추가 기능 (선택)

> **목표**: 설정 및 인증 (시간 여유 시)  
> **Sprint SP**: 21 (E7: 8 + E1: 13)

#### 🟡 E7 사용자 설정 `8 SP`

| User Story | 설명 | SP | 주요 Task |
|:---|:---|---:|:---|
| US 7.1 | 대화 톤/속도 설정 | 3 | SettingsActivity UI, 톤 Spinner, 속도 SeekBar |
| US 7.2 | 선호도 수정 | 2 | 선호도 수정 UI, 서버 업데이트 API |
| US 7.3 | 알림 시간 설정 | 3 | TimePicker, AlarmScheduler, AlarmManager |
| | **소계** | **8** | |

#### 🔴 E1 사용자 인증 및 온보딩 `13 SP` (선택)

| User Story | 설명 | SP | 주요 Task |
|:---|:---|---:|:---|
| US 1.1 | 사용자 회원가입 | 5 | LoginActivity UI, AuthViewModel, JWT 발급 |
| US 1.2 | 사용자 로그인 | 5 | TokenStorage, 서버 로그인 API, 자동 로그인 |
| US 1.3 | 초기 설정 (온보딩) | 3 | OnboardingActivity (ViewPager2), UserPreference |
| | **소계** | **13** | |

**📦 최소 목표**: SettingsActivity 완성, TTS 속도 반영

---

### W9 | 🟣 마무리: 최종 정리 & 발표

> **목표**: 버그 수정 및 시연 준비

| 태스크 | 담당 |
|:---|:---:|
| 통합 테스트 & 버그 수정 | All |
| UI/UX 다듬기 | App |
| 발표 자료 작성 | All |
| 시연 시나리오 연습 | All |
| 코드 정리 & README | All |

**📦 최종 산출물**: 완성된 앱 + 발표 자료 + 시연

---

## 🎯 주요 마일스톤

| 시점 | 마일스톤 | 설명 | 누적 SP |
|:---:|:---|:---|---:|
| Week 3 완료 | AI 서버 완성 | API로 대화 가능 | 34 |
| Week 5 완료 | **MVP 완성** 🎉 | 음성으로 대화 가능 | 55 |
| Week 6 완료 | 맞춤형 대화 | 건강/위치 데이터 반영 | 89 |
| Week 7 완료 | 일기 기능 | 대화 요약 저장 | 110 |
| Week 9 | 최종 발표 | 졸업 작품 완성 | 131+ |

---

## 📋 MVP 우선순위

### ✅ Must Have (MVP)

| Epic | 이름 | SP |
|:---:|:---|---:|
| E3 | 서버 AI 대화 생성 | 34 |
| E2 | 음성 대화 시스템 (앱) | 21 |
| | **소계** | **55** |

### ⭐ Should Have

| Epic | 이름 | SP |
|:---:|:---|---:|
| E4 | 건강 데이터 연동 | 13 |
| E5 | 위치 기반 대화 | 21 |
| E6 | 일기 요약 기능 | 13 |
| E9 | 데이터 관리 및 보안 | 8 |
| E7 | 사용자 설정 | 8 |
| | **소계** | **63** |

### 🔹 Nice to Have

| Epic | 이름 | SP |
|:---:|:---|---:|
| E1 | 사용자 인증 및 온보딩 | 13 |
| E8 | 알림 시스템 | 5 |
| E10 | 에러 처리 및 UX 개선 | 8 |
| | **소계** | **26** |

---

> **Last Updated**: 2025-01-12  
> **Team**: 치매 예방 회상 대화 시스템 개발팀
