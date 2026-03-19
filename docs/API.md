# Echo API 명세서

경도인지장애 분들의 치매 예방을 위한 AI 음성 대화 시스템 API

## Base URL

```
http://localhost:8080
```

## API 문서

- **Swagger UI**: http://localhost:8080/swagger-ui.html (서버 실행 필요, API 테스트 가능)
- **Postman Collection**: `server/docs/Echo_API.postman_collection.json`

> **Note**: 최신 API 스펙은 Swagger UI에서 확인하세요. 이 문서는 오프라인 참조 및 개발 가이드 용도입니다.

---

## Conversation API

대화 세션 관리 API

### 1. 대화 시작

새로운 대화 세션을 시작합니다.

- **URL:** `/api/conversations/start`
- **Method:** `POST`
- **Content-Type:** `application/json`

#### Request (HealthData)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| steps | Integer | X | 오늘 걸음 수 |
| sleepDurationMinutes | Integer | X | 수면 시간 (분 단위) |
| sleepStartTime | String | X | 취침 시각 ("HH:mm:ss" 형식) |
| wakeUpTime | String | X | 기상 시각 ("HH:mm:ss" 형식) |
| exerciseDistanceKm | Double | X | 운동 거리 (km 단위) |
| exerciseActivity | String | X | 운동 활동명 |
| activityList | String | X | 오늘 운동 활동 목록 |

```json
{
  "steps": 5000,
  "sleepDurationMinutes": 420,
  "sleepStartTime": "23:00:00",
  "wakeUpTime": "07:00:00",
  "exerciseDistanceKm": 2.5,
  "exerciseActivity": "산책",
  "activityList": "산책, 스트레칭"
}
```

> Health Connect에서 수집된 건강 데이터를 전송합니다. 모든 필드는 선택사항이며, 전송하지 않으면 서버에서 DB에 저장된 기존 데이터를 조회합니다.

#### Response

| 필드 | 타입 | 설명 |
|------|------|------|
| message | String | AI의 첫 인사 메시지 |
| audioData | byte[] | AI 응답 음성 (Base64 인코딩) |
| timestamp | LocalDateTime | 응답 생성 시간 |

```json
{
  "message": "안녕하세요! 오늘 하루는 어떠셨나요?",
  "audioData": "base64EncodedAudioData...",
  "timestamp": "2024-01-15T10:30:00"
}
```

---

### 2. 메시지 전송

사용자 음성 메시지를 처리하고 AI 응답을 반환합니다.

- **URL:** `/api/conversations/message`
- **Method:** `POST`
- **Content-Type:** `multipart/form-data`

#### Request

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| audio | File | O | 사용자 음성 파일 (WAV, MP3, M4A 지원) |

#### Response

| 필드 | 타입 | 설명 |
|------|------|------|
| userMessage | String | 사용자 음성을 텍스트로 변환한 메시지 (STT 결과) |
| aiResponse | String | AI 응답 텍스트 |
| audioData | byte[] | AI 응답 음성 (Base64 인코딩) |
| timestamp | LocalDateTime | 응답 생성 시간 |

```json
{
  "userMessage": "오늘 산책을 다녀왔어요",
  "aiResponse": "산책을 다녀오셨군요! 날씨가 좋았나요?",
  "audioData": "base64EncodedAudioData...",
  "timestamp": "2024-01-15T10:32:00"
}
```

#### 처리 흐름

```
사용자 음성 → STT(Whisper) → AI 응답 생성(GPT-4o-mini) → TTS(Azure) → 응답
```

---

### 3. 대화 종료

대화 세션을 종료하고 일기를 생성합니다.

- **URL:** `/api/conversations/end`
- **Method:** `POST`
- **Content-Type:** `application/json`

#### Request

없음

#### Response

| 필드 | 타입 | 설명 |
|------|------|------|
| endedAt | LocalDateTime | 대화 종료 시간 |

```json
{
  "endedAt": "2024-01-15T10:45:00"
}
```

> 대화 종료 시 DiaryService가 대화 내용을 일기 형식으로 변환하여 저장합니다.

---

### 4. TTS 재시도

마지막 AI 응답의 TTS 음성을 재생성합니다.

- **URL:** `/api/conversations/tts-retry`
- **Method:** `POST`
- **Content-Type:** `application/json`

#### Request

없음

#### Response

| 필드 | 타입 | 설명 |
|------|------|------|
| audioData | byte[] | 재생성된 AI 응답 음성 (Base64 인코딩) |

```json
{
  "audioData": "base64EncodedAudioData..."
}
```

> 네트워크 오류 등으로 TTS 음성을 받지 못한 경우 사용합니다.

---

## Voice API

음성 처리 API (STT/TTS)

### 1. STT (음성 → 텍스트)

음성 파일을 텍스트로 변환합니다.

- **URL:** `/api/voice/stt`
- **Method:** `POST`
- **Content-Type:** `multipart/form-data`
- **사용 API:** OpenAI Whisper

#### Request

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| file | File | O | 변환할 음성 파일 (WAV, MP3, M4A 지원) |

#### Response

| 필드 | 타입 | 설명 |
|------|------|------|
| text | String | 변환된 텍스트 |

```json
{
  "text": "오늘 날씨가 참 좋네요"
}
```

---

### 2. TTS (텍스트 → 음성)

텍스트를 음성으로 변환합니다.

- **URL:** `/api/voice/tts`
- **Method:** `POST`
- **Content-Type:** `application/json`
- **사용 API:** Azure Cognitive Services TTS

#### Request

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| text | String | O | 변환할 텍스트 |
| voiceSettings | Object | X | 음성 설정 |
| voiceSettings.voiceSpeed | Double | X | 음성 속도 (기본값: 1.0) |
| voiceSettings.voiceTone | String | X | 음성 톤 (기본값: "warm") |

```json
{
  "text": "안녕하세요, 오늘 하루는 어떠셨나요?",
  "voiceSettings": {
    "voiceSpeed": 1.0,
    "voiceTone": "warm"
  }
}
```

#### Response

- **Content-Type:** `audio/mpeg`
- **Body:** MP3 바이너리 오디오 데이터

---

## 에러 응답

### 공통 에러 형식

```json
{
  "timestamp": "2024-01-15T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "에러 메시지",
  "path": "/api/conversations/message"
}
```

### 주요 에러 코드

| 상태 코드 | 설명 |
|-----------|------|
| 400 | 잘못된 요청 (파일 형식 오류 등) |
| 404 | 리소스를 찾을 수 없음 (대화 컨텍스트 없음 등) |
| 500 | 서버 내부 오류 |

---

## 지원 파일 형식

### 음성 파일

- WAV
- MP3
- M4A

---

## 외부 API 연동

| API | 용도 | 상태 |
|-----|------|------|
| OpenAI Whisper | STT (음성→텍스트) | 구현 완료 |
| OpenAI GPT-4o-mini | AI 응답 생성 | 구현 완료 |
| Azure Cognitive Services TTS | TTS (텍스트→음성) | 구현 완료 |
| OpenWeatherMap | 날씨 정보 | 더미 구현 |

---

## 인증

현재 MVP 단계로 고정 userId=1을 사용합니다. 추후 JWT 인증 연동 예정입니다.
