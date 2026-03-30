# Epic 5: 위치 기반 대화 기능

## 개요

사용자의 실시간 위치와 방문 장소 정보를 활용하여 더욱 개인화된 회상 대화를 생성하는 기능입니다.

## 기능 목록

| User Story | 설명 | 플랫폼 | 상태 |
|------------|------|--------|------|
| US 5.1 | 위치 권한 요청 | Android | ✅ 완료 |
| US 5.2 | 현재 위치 조회 | Android | ✅ 완료 |
| US 5.3 | 역지오코딩 (좌표→주소) | Server | ✅ 완료 |
| US 5.4 | 날씨 정보 조회 | Server | ✅ 완료 |
| US 5.5 | GPS 경로 수집 | Android | ✅ 완료 |
| US 5.6 | 방문 장소 서버 전송 | Both | ✅ 완료 |
| US 5.7 | 방문 장소 기반 대화 | Server | ✅ 완료 |
| US 5.8 | 통합 테스트 및 QA | Both | 🔄 진행중 |

---

## 서버 구현 상세

### US 5.3: 역지오코딩 (좌표→주소)

**구현 파일:**
- `location/client/GeocodingClient.java` - Kakao Local API Feign 클라이언트
- `location/service/GeocodingService.java` - 역지오코딩 서비스

**API:**
- Kakao Local API (`/v2/local/geo/coord2address.json`)

**기능:**
```java
// 좌표 → 도시명
String cityName = geocodingService.getCityName(latitude, longitude);

// 좌표 → 전체 주소 + 장소명
GeocodingResult result = geocodingService.reverseGeocode(latitude, longitude);
// result.getPlaceName() → "스타벅스 강남점"
// result.getAddress() → "서울 강남구 테헤란로 101"
```

---

### US 5.4: 날씨 정보 조회

**구현 파일:**
- `common/client/WeatherClient.java` - OpenWeatherMap API 클라이언트
- `common/client/WeatherApiClient.java` - Feign 인터페이스

**API:**
- OpenWeatherMap Current Weather API (현재 날씨)
- OpenWeatherMap One Call API 3.0 Timemachine (과거 날씨)

**캐싱 전략:**

| 캐시 | TTL | 용도 |
|------|-----|------|
| `currentWeatherCache` | 30분 | 현재 날씨 |
| `visitWeatherCache` | 24시간 | 방문 시점 날씨 |

**기능:**
```java
// 현재 날씨 조회
WeatherData weather = weatherClient.getCurrentWeather(lat, lon);
// weather.getDescription() → "맑음"
// weather.getTemperature() → 18

// 방문 시점 날씨 조회 (Timemachine API)
VisitWeather visitWeather = weatherClient.getWeatherForVisit(lat, lon, visitStartTime);
```

---

### US 5.6: 방문 장소 서버 전송

**DTO 구조:**

```
앱에서 전송 (Raw)              서버 내부 (Enriched)
─────────────────────────      ─────────────────────────
RawLocationData                LocationData
├─ currentLatitude             ├─ currentCity (역지오코딩)
├─ currentLongitude            ├─ visitedPlaces[]
├─ visitedPlaces[]             │   ├─ placeName
│   ├─ latitude                │   ├─ address
│   ├─ longitude               │   ├─ weather (방문 시점)
│   ├─ visitStartTime          │   ├─ latitude
│   ├─ visitEndTime            │   ├─ longitude
│   └─ stayDurationMinutes     │   ├─ visitStartTime
└─ totalDistanceKm             │   ├─ visitEndTime
                               │   └─ stayDurationMinutes
                               └─ totalDistanceKm
```

**구현 파일:**
- `location/dto/RawLocationData.java` - 앱→서버 전송 DTO
- `location/dto/RawVisitedPlace.java` - 원시 방문 장소
- `location/dto/LocationData.java` - 보강된 위치 데이터
- `location/dto/VisitedPlace.java` - 보강된 방문 장소
- `location/service/LocationService.java` - 변환 서비스

**변환 로직:**
```java
public LocationData enrichLocationData(RawLocationData raw) {
    // 1. 현재 위치 → 도시명
    String currentCity = geocodingService.getCityName(lat, lon);

    // 2. 각 방문 장소 보강
    for (RawVisitedPlace rawPlace : raw.getVisitedPlaces()) {
        // 역지오코딩 → placeName, address
        // 방문 시점 날씨 조회 → weather
    }

    return LocationData.builder()
        .currentCity(currentCity)
        .visitedPlaces(enrichedPlaces)
        .totalDistanceKm(raw.getTotalDistanceKm())
        .build();
}
```

---

### US 5.7: 방문 장소 기반 대화

**데이터 흐름:**

```
ConversationStartRequest
    └─ locationData: RawLocationData
           ↓
ContextService.initializeContext()
    ├─ LocationService.enrichLocationData() → LocationData
    ├─ WeatherClient.getCurrentWeather() → 현재 날씨
    └─ UserContext 저장
           ↓
PromptService.buildSystemPrompt()
    ├─ {{currentCity}} = locationData.currentCity
    └─ {{visitedPlacesText}} = 방문 장소 목록 (체류 시간 순)
           ↓
프롬프트 템플릿 v7
    └─ AI가 방문 장소 기반 대화 시작
```

**프롬프트 템플릿 v7 주요 내용:**

```
[오늘의 데이터]
현재 위치: {{currentCity}}
[방문 장소] (체류 시간 순, 방문 시점 날씨 포함)
{{visitedPlacesText}}

[대화 진입점 결정]
▷ [방문 장소]에 구체적인 장소명이 있는 경우 (최우선)
  → 가장 오래 머문 장소부터 활동 회상을 시작한다.
  → 방문 시점 날씨를 활용해 그때 기분이나 상황을 자연스럽게 묻는다.
```

**방문 장소 텍스트 포맷:**
```
- 스타벅스 강남점 (90분, 14:00~15:30) (날씨: 맑음, 18°C)
- 강남역 (30분, 16:00~16:30) (날씨: 구름 많음, 16°C)
```

---

## 환경 설정

### 필수 API 키

`application-local.yaml`:

```yaml
# Kakao Local API (역지오코딩)
kakao:
  api:
    key: "your-kakao-rest-api-key"

# OpenWeatherMap API (날씨)
weather:
  api:
    key: "your-openweathermap-api-key"
```

### API 키 발급

| API | 발급 URL | 비고 |
|-----|----------|------|
| Kakao | https://developers.kakao.com | REST API 키 사용 |
| OpenWeatherMap | https://openweathermap.org/api | One Call API 3.0 구독 필요 |

---

## 테스트

### 통합 테스트 실행

```bash
# 위치 데이터 통합 테스트 (S16)
./gradlew test --tests "com.example.echo.integration.LocationDataIntegrationTest"
```

### 테스트 시나리오

| 테스트 | 설명 |
|--------|------|
| `startConversation_withLocationData` | 위치 데이터 포함 대화 시작 |
| `startConversation_withMultipleVisitedPlaces` | 여러 방문 장소 처리 |
| `startConversation_withoutLocationData` | 위치 없음 폴백 |
| `startConversation_emptyVisitedPlaces` | 방문 장소 없음 |
| `fullScenario_withLocationData` | E2E 전체 시나리오 |

### 단위 테스트

```bash
# LocationService 단위 테스트
./gradlew test --tests "com.example.echo.location.service.LocationServiceTest"

# WeatherClient 단위 테스트
./gradlew test --tests "com.example.echo.common.client.WeatherClientUnitTest"
```

---

## API 스키마

### 대화 시작 요청

**POST** `/api/conversations/start`

```json
{
  "healthData": {
    "stepCount": 5000,
    "sleepMinutes": 420,
    "exerciseDistanceKm": 2.5,
    "exerciseActivityName": "걷기"
  },
  "locationData": {
    "currentLatitude": 37.8813,
    "currentLongitude": 127.7298,
    "visitedPlaces": [
      {
        "latitude": 37.8813,
        "longitude": 127.7298,
        "visitStartTime": "10:00:00",
        "visitEndTime": "11:30:00",
        "stayDurationMinutes": 90
      }
    ],
    "totalDistanceKm": 2.5
  }
}
```

### 응답

```json
{
  "message": "오늘 공지천 산책로에 다녀오셨네요! 날씨가 맑아서 걷기 좋았겠어요.",
  "audioData": "base64...",
  "timestamp": "2026-03-30T10:00:00"
}
```

---

## 관련 이슈

- [#222](../../issues/222) US 5.3 역지오코딩
- [#223](../../issues/223) US 5.4 날씨 정보 조회
- [#225](../../issues/225) US 5.6 방문 장소 서버 전송
- [#226](../../issues/226) US 5.7 방문 장소 기반 대화
- [#227](../../issues/227) US 5.8 통합 테스트 및 QA
