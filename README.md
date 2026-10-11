# echo
회상 요법을 통한 치매 예방 시스템

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-6DB33F?logo=springboot&logoColor=white)
![Android](https://img.shields.io/badge/Android-Jetpack%20Compose-3DDC84?logo=android&logoColor=white)

### 목차
- [📖 개요](#-개요)
- [🚀 빠른 시작](#-빠른-시작)
- [📁 시스템 구성도](#-시스템-구성도)
- [📂 프로젝트 구조](#-프로젝트-구조)
- [✨ 주요 기능](#-주요-기능)
- [🛠️ 개발 환경](#️-개발-환경)
- [🏗️ 운영 환경](#️-운영-환경)
- [🔍 데모 환경](#-데모-환경)
- [📚 문서](#-문서)

## 📖 개요
본 프로젝트는 경도인지장애를 가진 분들이 일상 속에서 자연스럽게 기억을 회상하고, 긍정적인 감정을 유지할 수 있도록 돕는 AI 음성 대화 시스템입니다.
사용자의 건강 데이터(갤럭시 워치 → Health Connect), 위치 정보, 날씨, 개인 선호도를 활용하여 맞춤형 회상 대화를 생성하고, 하루의 대화를 일기 형식으로 요약하여 기록합니다.
지난 대화에서 나온 이야기는 장기기억으로 쌓아 두었다가 이어지는 화제가 나오면 대화에 다시 꺼내 쓰며, 말하는 동안 음성을 실시간으로 전사하고 AI 응답을 조각 단위로 스트리밍 재생하여 응답 대기 시간을 줄였습니다.

## 🚀 빠른 시작
```bash
# 서버 (server/)
./gradlew build      # 빌드
./gradlew test       # 테스트
./gradlew bootRun    # 실행 (Swagger UI: http://localhost:8080/swagger-ui.html)

# Android (android/)
./gradlew assembleDebug   # 디버그 APK 빌드
./gradlew installDebug    # 기기 설치
```

## 📁 시스템 구성도
![시스템 구성도](docs/images/구성도.png)

## 📂 프로젝트 구조
```
echo/
├─ android/   # Android 클라이언트 앱 (Kotlin, Jetpack Compose)
├─ server/    # 백엔드 서버 (Java 17, Spring Boot)
└─ docs/      # API 명세 등 프로젝트 문서
```

## ✨ 주요 기능
| 기능 | 설명 |
| :--- | :--- |
| **AI 음성 대화** | STT → AI 응답 생성(OpenRouter `gpt-5.6-terra`) → TTS(ElevenLabs `eleven_v3`, 설정으로 Azure Speech 선택 가능) |
| **실시간 음성 전사** | 앱이 Silero VAD로 말 끝을 판정(무음 2초)하고, 말하는 동안 음성을 WebSocket(`/api/conversations/message-live`)으로 보내 OpenAI Realtime 전사를 진행 |
| **스트리밍 응답** | LLM이 생성하는 대로 문장 조각 단위로 TTS하여 첫 조각부터 재생 (`/start-stream`, `/message-stream`), 말풍선도 조각마다 이어 붙임 |
| **장기기억 (RAG)** | 대화에서 나온 사실을 임베딩(`text-embedding-3-large`)으로 누적 저장하고, 대화 중 어르신 발화와 이어지는 기억을 검색해 대화에 반영 |
| **맞춤형 회상 질문** | 건강 데이터·방문 장소·날씨·사용자 선호도를 프롬프트에 주입하여 개인화된 대화 생성 |
| **건강 데이터 수집** | 갤럭시 워치 → Health Connect 연동 (걸음 수, 수면, 운동 거리, 운동 활동명) |
| **위치 기반 대화** | 백그라운드 GPS 수집 → 체류 지점(Stay Point) 분석 → Kakao API 역지오코딩으로 방문 장소 파악 |
| **루틴 방문 장소** | 회사·병원 등 자주 가는 장소를 카테고리화하고 요일/시간을 직접 수정 가능 |
| **일기 생성** | 대화 종료 시 하루 대화 내용을 일기로 요약·저장 (하루 1개, 대화할 때마다 증분 갱신) |
| **일기 탭** | 일기를 캘린더로 조회하고 앱에 캐시해 오프라인에서도 열람, 날짜를 누르면 그날의 대화 말풍선까지 확인 |
| **회원 관리** | JWT(Access/Refresh Token) 기반 회원가입·로그인 |
| **개인정보 보호** | 사용자 민감정보 컬럼 AES-256-GCM 암호화, 앱 로컬 DB SQLCipher 암호화 |

## 🛠️ 개발 환경
| 구분 | 서버 | 안드로이드 클라이언트 |
| :--- | :--- | :--- |
| **운영체제** | Windows | Windows |
| **언어** | Java 17 | Kotlin 2.2.10 |
| **프레임워크** | Spring Boot 3.5.9 | Jetpack Compose |
| **빌드 도구** | Gradle 8.14.3 | Gradle 9.4.1 (AGP 9.2.1) |
| **DB** | MySQL 8.0 (로컬) / AWS RDS MySQL (운영) | Room 2.8.4 (SQLCipher 암호화) |
| **주요 라이브러리** | Spring Data JPA, Spring Security(JWT), OpenFeign(Apache HttpClient 5 커넥션 풀), Spring WebSocket, Caffeine | Retrofit, OkHttp(WebSocket), Coroutines, Health Connect, Navigation Compose, Media3, Silero VAD |
| **외부 API** | OpenRouter (`gpt-5.6-terra` 대화 생성), OpenAI (Whisper STT, Realtime 실시간 전사, Embeddings 장기기억), ElevenLabs TTS, Azure Speech TTS(선택), Kakao(역지오코딩), OpenWeatherMap | — |
| **IDE** | IntelliJ IDEA | Android Studio |
| **운영 환경** | AWS EC2(Amazon Linux 2023) + Docker, Nginx/HTTPS, GitHub Actions CI/CD | — |

## 🏗️ 운영 환경
| 구분 | 상세 사양 |
| :--- | :--- |
| **클라우드** | AWS (EC2, RDS) |
| **서버 OS** | Amazon Linux 2023 |
| **웹 서버** | Nginx (HTTPS 적용) |
| **배포 방식** | GitHub Actions, Docker(재생성 배포) |
| **DB 사양** | RDS MySQL |

## 🔍 데모 환경
| 구분 | 상세 사양 | 비고 |
| :--- | :--- | :--- |
| **데모 서버** | AWS EC2 | vCPU: 2, RAM: 2GB (Amazon Linux 2023) |
| **DB** | RDS MySQL |
| **데모 앱** | GitHub Actions, Docker(재생성 배포) |
| **AI 모델** | `gpt-5.6-terra` (OpenRouter) |

**실시간 회상 대화 데모 플로우**
| 단계 (Step) | 활동 (Action) | 기술적 배경 (Technical Flow) |
|------------|---------------|-------------------------------|
| Step 1 | 실시간 활동 | 시연자가 갤럭시 워치를 착용하고 야외 활동을 수행하며, 워치 센서가 걸음 수·수면·운동 데이터를 기록하고 앱이 백그라운드에서 GPS 경로를 수집 |
| Step 2 | 기기간 동기화 | 산책 종료 후 스마트폰 근처로 이동하면 워치 데이터가 스마트폰으로 전달되고, Health Connect를 통해 자동 동기화됨 |
| Step 3 | 대화 시작 (Click) | 앱 메인 화면의 **[대화 시작하기]** 버튼 클릭 시 백그라운드에서 자동 실행됨<br/>1) Health Connect: 오늘 건강 데이터(걸음 수, 수면, 운동)및 위치 데이터 추출<br/>2) Stay Point Detection: 방문 장소(예: 정왕 시장) 및 체류 시간 계산 |
| Step 4 | AI 맥락 통합 | 분석된 건강·위치 데이터를 프롬프트에 자동 주입하고, `[정왕 시장 + 3,000보 + 오늘 날씨]` 맥락 데이터를 서버로 즉시 전송 |
| Step 5 | 맞춤형 대화 생성 | AI가 분석된 맥락을 기반으로 TTS 음성 응답을 생성<br/>예시: **“영희님, 어서 오세요! 방금 정왕 시장에서 3,000보나 걷고 오셨네요? 기분은 좀 어떠신가요?”** |

## 📚 문서
- **API 명세**: [docs/API.md](docs/API.md) (Swagger UI: 서버 실행 후 `/swagger-ui.html`)
- **Postman Collection**: `server/docs/Echo_API.postman_collection.json`
- **일기 탭 QA 계획**: [docs/diary-tab-qa-plan.md](docs/diary-tab-qa-plan.md)
- **개발 가이드**: [server/CLAUDE.md](server/CLAUDE.md), [android/CLAUDE.md](android/CLAUDE.md)
