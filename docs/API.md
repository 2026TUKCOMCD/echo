# Echo API 명세서

경도인지장애 분들의 치매 예방을 위한 AI 음성 대화 시스템 API

## Base URL

```
http://localhost:8080
```

---

## Conversation API

대화 세션 관리 API

### 1. 대화 시작

새로운 대화 세션을 시작합니다.

- **URL:** `/api/conversations/start`
- **Method:** `POST`
- **Content-Type:** `application/json`

#### Request

없음 (userId는 서버에서 자동 처리, MVP 단계에서는 고정 userId=1 사용)

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
| audio | File | O | 사용자 음성 파일 (mp3, wav, webm 등) |

#### Response

| 필드 | 타입 | 설명 |
|------|------|------|
| userMessage | String | 사용자 음성을 텍스트로 변환한 메시지 |
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
사용자 음성 → STT(Whisper) → AI 응답 생성(GPT-4o-mini) → TTS(Clova) → 응답
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

> 대화 종료 시 DiaryService가 비동기로 대화 내용을 일기 형식으로 변환하여 저장합니다.

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
| file | File | O | 변환할 음성 파일 (mp3, wav, webm 등) |

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
- **사용 API:** Clova TTS

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
| 404 | 리소스를 찾을 수 없음 |
| 500 | 서버 내부 오류 |

---

## 지원 파일 형식

### 음성 파일

- mp3
- wav
- webm
- m4a
- ogg

---

## 외부 API 연동

| API | 용도 | 상태 |
|-----|------|------|
| OpenAI Whisper | STT (음성→텍스트) | 구현 완료 |
| OpenAI GPT-4o-mini | AI 응답 생성 | 구현 완료 |
| Clova TTS | TTS (텍스트→음성) | 구현 완료 |
