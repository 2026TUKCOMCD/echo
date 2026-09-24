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

### 1-1. 대화 시작 (스트리밍 응답)

`/start`와 같은 처리(컨텍스트 초기화 → 첫 인사 생성 → TTS)를 하되, 첫 인사를 LLM이 생성하는 대로 조각 단위로 TTS해 오디오를 내려보냅니다. 응답 규격과 처리 방식은 [2-1절](#2-1-메시지-전송-스트리밍-응답)과 같습니다.

- **URL:** `/api/conversations/start-stream`
- **Method:** `POST`
- **Content-Type (요청):** `application/json` (본문은 `/start`와 동일, 생략 가능)
- **Content-Type (응답):** `application/x-echo-stream`

#### 응답 본문 (프레임 스트림)

프레임 규격은 `/message-stream`과 완전히 같습니다. 단, **첫 인사에는 사용자 발화가 없으므로 META의 `userMessage`는 항상 `null`** 이고, 인사말은 TEXT 프레임으로 옵니다.

```
META {"userMessage": null}
TEXT {"text": "안녕하세요, 어르신! 오늘 하루는 어떠셨어요?"}  → AUDIO ...
TEXT {"text": "오늘은 날씨가 참 맑네요."}                     → AUDIO ...
END
```

- 기존 `/start`는 그대로 유지됩니다. 클라이언트는 404/405일 때만 `/start`로 폴백합니다.
- 오류·히스토리 규칙은 [2-1절](#2-1-메시지-전송-스트리밍-응답)과 같습니다.

#### 지연 측정 로그

`[지연측정]` 로그에 다음 구간이 남습니다. 인사말과 응답의 통계가 섞이지 않도록 `/message-stream`과 stage 이름을 분리했습니다.

| stage | 의미 |
|---|---|
| `start_llm_first_token` | LLM 요청 ~ 첫 토큰 |
| `start_llm_first_chunk` | LLM 요청 ~ 첫 조각 확정 |
| `llm_greeting` | LLM 전체 (스트리밍 전과 같은 의미 → 전후 비교용) |
| `start_tts_first_byte` | 첫 조각 TTS 요청 ~ 첫 오디오 바이트 |
| `start_tts_rest_first_byte` | 나머지 조각 TTS 요청 ~ 첫 오디오 바이트 |
| `start_first_audio` | 요청 ~ 첫 오디오 (일괄 방식의 `start_total`에 대응) |
| `start_tts_stream_total` | 첫 조각 TTS 요청 ~ 스트림 전송 완료 |

---

### 2. 메시지 전송

사용자 음성 메시지를 처리하고 AI 응답을 반환합니다.

- **URL:** `/api/conversations/message`
- **Method:** `POST`
- **Content-Type:** `multipart/form-data`

#### Request

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| audio | File | O | 사용자 음성 파일 (WAV, MP3, M4A, WebM, MP4 지원) |

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

### 2-1. 메시지 전송 (스트리밍 응답)

`/message`와 같은 처리(STT → AI → TTS)를 하되, **AI 응답을 LLM이 생성하는 대로 조각 단위로 TTS해 오디오를 내려보냅니다.** 첫 소리가 나오기까지의 대기 시간을 줄이기 위한 엔드포인트이며, 기존 `/message`는 그대로 유지됩니다.

AI 응답은 **"첫 조각 + 나머지" 두 조각**으로 나뉩니다. 첫 조각이 확정되는 즉시 TTS를 시작하고, 나머지는 LLM이 끝나면 한 번에 합성합니다(첫 조각이 재생되는 동안 준비). eleven_v3가 조각을 자연스럽게 이어 주는 request stitching을 지원하지 않아 이음새를 1곳으로 제한했습니다.

- 문장 끝: `. ? ! … ~`와 줄바꿈. 뒤에 공백이 와야 끝으로 봅니다(`1.5km`의 소수점 제외).
- 첫 조각은 최소 `conversation.stream.first-chunk-min-chars`(기본 15)자 — 첫 문장이 짧으면 다음 문장까지 붙입니다.
- 문장 끝 없이 `first-chunk-max-chars`(기본 80)자를 넘으면 쉼표/공백에서 자릅니다.
- 응답이 짧아 첫 조각 기준을 못 채우면 조각 하나로 끝납니다.

- **URL:** `/api/conversations/message-stream`
- **Method:** `POST`
- **Content-Type (요청):** `multipart/form-data` (`audio` 필드는 `/message`와 동일)
- **Content-Type (응답):** `application/x-echo-stream`

#### 응답 본문 (프레임 스트림)

본문은 프레임의 연속입니다. 프레임 = `[1바이트 type][4바이트 big-endian payload 길이][payload]`

| type | 이름 | 개수 | payload |
|------|------|------|---------|
| `0x01` | META | 맨 앞에 정확히 1개 | UTF-8 JSON `{"userMessage": "..."}` (STT 결과) |
| `0x03` | TEXT | 조각마다 1개 | UTF-8 JSON `{"text": "..."}` — AI 응답 조각 텍스트. **그 조각의 AUDIO들 바로 앞**에 옵니다 |
| `0x02` | AUDIO | 0개 이상 | mp3 바이트 조각 (이어 붙이면 재생 가능한 mp3) |
| `0x00` | END | 마지막에 1개 | 없음 (길이 0) — **정상 종료 표시** |

```
META {"userMessage": "오늘 산책을 다녀왔어요"}
TEXT {"text": "그러셨군요! 오늘 산책은 어디로 다녀오셨어요?"}  → AUDIO ...
TEXT {"text": "날씨가 좋아서 걷기 좋으셨겠어요."}              → AUDIO ...
END
```

- 클라이언트는 TEXT를 받은 순서대로 말풍선에 이어 붙입니다(조각 사이는 공백 하나). 전체 AI 응답 = 모든 TEXT를 공백으로 이은 것이며, 서버 히스토리에 저장되는 값과 같습니다.
- 대화 내용(텍스트)은 민감정보라서 **HTTP 헤더가 아니라 본문으로만** 전달합니다.
- **END 프레임 없이 스트림이 끝나면 잘린 것으로 간주**하고 `/tts-retry`로 폴백해야 합니다. (nginx→서버 구간이 HTTP/1.0이면 "정상 종료"와 "끊김"을 연결 종료만으로 구분할 수 없어 END로 명시합니다.)
- 응답에 `X-Accel-Buffering: no`가 포함되어 nginx가 청크를 버퍼링하지 않습니다.

#### 실패와 히스토리 규칙 — "히스토리 = 어르신이 들은(들을) 말"

| 경우 | 응답 | 히스토리 |
|---|---|---|
| 첫 소리 전 실패 (STT, 첫 조각 전 LLM, 첫 조각 TTS) | `/message`와 같은 JSON 오류 (HTTP 4xx/5xx) | 저장 안 함 (LLM 연결도 끊음) |
| 첫 조각 이후 LLM 실패 | 첫 조각만 보내고 **END로 정상 종료** | 첫 조각만 AI 응답으로 저장 |
| 나머지 조각 TTS 실패/끊김 | END 없이 종료 → 클라이언트가 `/tts-retry` | 전체 응답 저장됨 (`/tts-retry`가 전체를 다시 합성) |
| 클라이언트 연결 끊김 | — | LLM을 끝까지 받아 전체 저장 (나머지 TTS는 생략) |

#### 지연 측정 로그

| stage | 의미 |
|---|---|
| `stt` | STT |
| `llm_first_token` | LLM 요청 ~ 첫 토큰 |
| `llm_first_chunk` | LLM 요청 ~ 첫 조각 확정 |
| `llm` | LLM 전체 (스트리밍 전과 같은 의미 → 전후 비교용) |
| `tts_first_byte` | 첫 조각 TTS 요청 ~ 첫 오디오 바이트 |
| `tts_rest_first_byte` | 나머지 조각 TTS 요청 ~ 첫 오디오 바이트 |
| `message_first_audio` | 요청 ~ 첫 오디오 (체감 지연) |
| `tts_stream_total` | 첫 조각 TTS 요청 ~ 스트림 전송 완료 |

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
| aiResponse | String | 다시 합성한 마지막 AI 응답 전체 텍스트 |
| audioData | byte[] | 재생성된 AI 응답 음성 (Base64 인코딩) |

```json
{
  "aiResponse": "그러셨군요! 오늘 산책은 어디로 다녀오셨어요? 날씨가 좋아서 걷기 좋으셨겠어요.",
  "audioData": "base64EncodedAudioData..."
}
```

> 네트워크 오류 등으로 TTS 음성을 받지 못한 경우 사용합니다. 스트리밍 응답이 중간에 끊기면 클라이언트는 받은 조각까지만 알고 있으므로 `aiResponse`로 말풍선을 전체 문장으로 채웁니다.

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
| file | File | O | 변환할 음성 파일 (WAV, MP3, M4A, WebM, MP4 지원) |

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
- WebM
- MP4

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
