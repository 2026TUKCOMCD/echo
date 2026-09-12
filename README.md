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
![제목 없음](https://github.com/user-attachments/assets/f75526ae-6124-46e2-906b-58ff658536e6)

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
| **AI 음성 대화** | STT(OpenAI Whisper) → AI 응답 생성(GPT-4o-mini) → TTS(ElevenLabs, 실패 시 Azure Speech 폴백) |
| **맞춤형 회상 질문** | 건강 데이터·방문 장소·날씨·사용자 선호도를 프롬프트에 주입하여 개인화된 대화 생성 |
| **건강 데이터 수집** | 갤럭시 워치 → Health Connect 연동 (걸음 수, 수면, 운동 거리, 운동 활동명) |
| **위치 기반 대화** | 백그라운드 GPS 수집 → 체류 지점(Stay Point) 분석 → Kakao API 역지오코딩으로 방문 장소 파악 |
| **일기 생성** | 대화 종료 시 하루 대화 내용을 일기 형식으로 요약·저장 |
| **회원 관리** | JWT(Access/Refresh Token) 기반 회원가입·로그인 |

## 🛠️ 개발 환경
| 구분 | 서버 | 안드로이드 클라이언트 |
| :--- | :--- | :--- |
| **운영체제** | Windows | Windows |
| **언어** | Java 17 | Kotlin 2.2.10 |
| **프레임워크** | Spring Boot 3.5.9 | Jetpack Compose |
| **빌드 도구** | Gradle 8.14.3 | Gradle (AGP 9.0.0) |
| **DB** | MySQL 8.0 (로컬) / AWS RDS MySQL (운영) | Room 2.8.4 (SQLCipher 암호화) |
| **주요 라이브러리** | Spring Data JPA, Spring Security(JWT), OpenFeign, Caffeine | Retrofit, Coroutines, Health Connect, Navigation Compose |
| **외부 API** | OpenAI (GPT-4o-mini, Whisper STT), ElevenLabs TTS, Azure Speech TTS(폴백), Kakao(역지오코딩), OpenWeatherMap | — |
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
| **AI 모델** | GPT-4o-mini |

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
