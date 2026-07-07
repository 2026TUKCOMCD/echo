# 미탐색 영역 버그 탐색 계획 (Haiku 수행용)

## 배경

지금까지 아래 세 영역에서 버그 탐색을 완료했다 (이 계획의 대상이 **아님**):

- 위치 수집: `data/location/` (LocationScheduler, LocationCollectionService 등)
- 대화 흐름: `presentation/conversation/ConversationViewModel` 상태머신
- 설정 화면: `presentation/settings/SettingsViewModel` + 대화 알람 스케줄링(`data/alarm/`)

이 문서는 **그 외 영역**(Android 클라이언트 + 서버)을 탐색하기 위한 계획이다.
각 태스크는 독립적이며, 한 세션에 하나의 태스크만 수행한다.

**경로 표기 규칙**: T1~T9의 경로는 별도 표기가 없으면
`android/app/src/main/java/com/example/graduation_project/` 기준이다.
T10~T11의 경로는 `server/src/main/java/com/example/echo/` 기준이다.
그 외 위치의 파일은 리포지토리 루트부터 전체 경로로 표기한다.

## 수행 규칙 (모든 태스크 공통)

1. **읽기 전용 탐색이다. 소스 코드를 수정하지 않는다.** 발견 사항은 보고서로만 남긴다.
2. 태스크에 명시된 파일을 **전부 읽은 뒤** 체크리스트를 순서대로 확인한다.
   명시되지 않은 파일은 호출 관계 확인에 필요할 때만 연다.
3. 발견 보고 기준:
   - 어떤 입력/상황에서 어떤 잘못된 동작이 나는지 **구체적 시나리오를 서술할 수
     없으면 보고하지 않는다.** (스타일/네이밍 지적, 리팩터링 제안도 제외)
   - 시나리오는 서술할 수 있지만 코드만으로 확증할 수 없으면(실행/재현이 필요하면)
     보고하되 심각도 옆에 `[요검증]`을 붙인다.
4. 심각도 기준:
   - **상**: 크래시, 데이터 유실/오염, 보안 취약점, 기능 전체 불능
   - **중**: 특정 조건에서 기능 오동작, 리소스 누수, 잘못된 데이터 표시
   - **하**: 드문 엣지케이스, 영향이 제한적인 문제
5. 발견마다 아래 형식으로 기록한다:
   ```
   ### [심각도: 상/중/하] [요검증(해당 시)] 한 줄 요약
   - 파일: 경로:줄번호
   - 증상: 어떤 입력/상황에서 어떤 잘못된 동작이 발생하는지 (구체적 시나리오)
   - 근거: 해당 코드 조각과 문제가 되는 이유
   ```
6. 결과는 `docs/bug-hunt-results/<태스크ID>.md` 파일로 저장한다.
   **발견 유무와 관계없이** 체크리스트 각 항목의 판정(문제 없음 / 발견 #N / 확인
   불가와 그 이유)을 함께 기록한다.

### 발견 보고 예시 (이 수준의 구체성을 갖출 것)

```
### [심각도: 중] 재생 실패 시 임시 오디오 파일이 삭제되지 않고 누적됨
- 파일: data/voice/AudioFileManager.kt:52
- 증상: 서버가 손상된 base64 오디오를 내려주면 디코딩 예외가 발생하는데,
  이때 이미 생성된 임시 .wav 파일이 삭제되지 않는다. 네트워크가 불안정한
  환경에서 대화를 반복하면 임시 파일이 계속 쌓여 저장 공간을 잠식한다.
- 근거: createTempFile() 호출(52행) 이후의 decode 블록(58행)에 try/finally가
  없어, 58행에서 예외가 던져지면 61행의 delete()에 도달하지 않는다.
```

## 공통 점검 관점 (체크리스트가 이를 구체화한다)

- **동시성**: 코루틴 스코프 수명, 공유 가변 상태, race condition, 중복 실행 가드 부재
- **수명주기**: Service/Receiver/ViewModel 수명과 비동기 작업의 불일치, 리소스 해제 누락
- **에러 경로**: 예외를 삼키는 catch, 실패 시 상태 복구 누락, 부분 실패 처리
- **경계값**: null/빈 리스트/타임아웃/권한 거부/프로세스 재시작 직후

---

## 태스크 목록

### T1. 음성 녹음 파이프라인

- **대상 파일**: `data/voice/` 중 VoiceRecordingManager, AudioRecordManager,
  AudioRecorder, VadProcessor, WavConverter, AudioFileManager
  + `domain/voice/`의 상태/리스너 정의
- **체크리스트**:
  - [ ] 녹음 시작/중지가 빠르게 반복될 때 상태 전이가 꼬이지 않는가
    (start 중 stop 호출, stop 완료 전 재 start 등)
  - [ ] AudioRecord/버퍼/스레드가 모든 종료 경로(정상 종료, 예외, 취소)에서 해제되는가
  - [ ] VadProcessor의 콜백(VadListener)이 어느 스레드에서 호출되는지,
    수신 측이 그 스레드를 가정해도 안전한지
  - [ ] WavConverter의 헤더 계산(샘플레이트, 채널, 데이터 길이)이 실제 녹음 설정과 일치하는가
  - [ ] AudioFileManager가 만든 임시 파일이 실패 경로에서도 삭제되는가 (파일 누적 여부)
  - [ ] 녹음 중 마이크 권한이 회수되거나 오디오 포커스를 잃으면 어떻게 되는가

### T2. 오디오 재생

- **대상 파일**: `data/voice/AudioPlayerManager.kt`,
  `domain/voice/AudioPlayState.kt`, `AudioPlayListener.kt`, `AudioPlayException.kt`
- **호출부 확인**: `presentation/conversation/ConversationViewModel.kt`는 대화
  상태머신 탐색이 이미 끝난 파일이므로 전체를 읽지 말고, AudioPlayerManager를
  호출하는 아래 다섯 함수**만** 읽는다:
  `setupAudioPlayListener()`, `playAiAudio()`, `endConversation()`,
  `onAppBackgrounded()`, `onCleared()`
- **체크리스트**:
  - [ ] 재생 중 새 재생 요청이 오면 이전 MediaPlayer가 확실히 release 되는가
  - [ ] prepare/start 실패 시 리스너에 에러가 전달되고 상태가 복구되는가
  - [ ] 재생 완료/에러 콜백이 중복 호출될 가능성은 없는가
  - [ ] 위 다섯 호출 함수에서 stop()/release() 호출 시점이 재생 상태와 어긋날 수
    있는가 (예: 재생 시작 직전에 백그라운드 전환)

### T3. 네트워크 계층과 토큰 갱신

- **대상 파일**: `data/api/` 전체
  (ApiClient, ApiResult, AuthInterceptor, TokenAuthenticator 포함),
  `data/local/TokenStorage.kt`, `data/repository/AuthRepository.kt`
- **체크리스트**:
  - [ ] 여러 요청이 동시에 401을 받으면 refresh가 중복 실행되는가 (동기화 여부)
  - [ ] refresh 실패(refresh 토큰 만료) 시 무한 재시도 루프가 생길 수 있는가,
    로그아웃 처리로 이어지는가
  - [ ] TokenAuthenticator 안에서 이미 갱신된 토큰으로 재시도하는지,
    옛 토큰으로 다시 refresh를 시도하는지
  - [ ] TokenStorage 읽기/쓰기의 스레드 안전성 (Interceptor는 OkHttp 스레드에서 돈다)
  - [ ] ApiResult 변환에서 HTTP 에러 바디/타임아웃/IOException이 각각 올바른 타입으로 분류되는가
  - [ ] 로그아웃 시 토큰이 모든 저장 위치에서 지워지는가

### T4. 로컬 DB (Room)

- **대상 파일**: `data/local/AppDatabase.kt`, `data/local/dao/` 전체,
  `data/local/entity/` 전체, `data/local/mapper/MessageMapper.kt`
- **사용처 확인**: 정규식 `MessageDao|LocationPointDao`로
  `android/app/src/main/java` 아래를 검색해 DAO 호출부를 찾는다
- **체크리스트**:
  - [ ] DB 버전과 마이그레이션 정의가 일치하는가, `fallbackToDestructiveMigration`
    사용 시 데이터 유실이 의도된 것인가
  - [ ] suspend가 아닌 DAO 호출이 메인 스레드에서 실행될 수 있는 경로가 있는가
  - [ ] LocationPointEntity의 삭제/정리(오래된 데이터 제거) 로직이 존재하는가 — 무한 증가 여부
  - [ ] MessageMapper에서 nullable 필드, enum/문자열 변환 실패 시 동작
  - [ ] 같은 데이터에 대한 insert 충돌 전략(OnConflictStrategy)이 의도와 맞는가

### T5. 부팅/업데이트 후 복구

- **대상 파일**: `data/receiver/BootReceiver.kt`,
  `data/alarm/ConversationAlarmStorage.kt`, `data/location/LocationCollectionStorage.kt`,
  `android/app/src/main/AndroidManifest.xml`의 receiver/service 선언부
- **체크리스트**:
  - [ ] BootReceiver가 위치 수집 알람과 대화 알람을 **둘 다** 복원하는가
  - [ ] BootReceiver는 BOOT_COMPLETED 외에 MY_PACKAGE_REPLACED(앱 업데이트)도
    처리한다 — 업데이트 직후 시나리오에서도 복원 로직이 안전한가
    (업데이트로 저장 형식이 바뀐 경우 등)
  - [ ] Receiver 안에서 비동기 작업(코루틴/IO)을 하면 프로세스가 먼저 죽을 수 있는데,
    goAsync()나 동기 처리로 보호되는가
  - [ ] 알람 복원 시 과거 시각이 저장돼 있으면 즉시 발화/과거 시각 스케줄이 되는가
  - [ ] Manifest에 RECEIVE_BOOT_COMPLETED 권한과 intent-filter가 올바른가,
    exported 설정이 적절한가

### T6. 인증 화면 (로그인/회원가입)

- **대상 파일**: `presentation/auth/` 전체 (LoginScreen/ViewModel, SignupScreen/ViewModel),
  `data/repository/AuthRepository.kt`, `data/model/AuthModels.kt`
- **범위 주의**: 토큰 갱신/저장의 동시성은 T3에서 다루므로 여기서는 보고하지
  않는다. 이 태스크는 화면·ViewModel 흐름에 집중한다.
- **체크리스트**:
  - [ ] 로그인 버튼 연타 시 요청이 중복 발사되는가 (로딩 가드 여부)
  - [ ] 로그인 성공 후 화면 전환 전에 ViewModel이 파괴되면 토큰 저장이 유실되는가
    (viewModelScope vs 더 긴 스코프)
  - [ ] 입력 검증(이메일 형식, 비밀번호, 생년월일)이 클라이언트에서 빠지는 경로
  - [ ] 에러 메시지가 일회성 이벤트로 처리되는가 (화면 회전 시 재표시 여부)
  - [ ] 회원가입 성공 직후 자동 로그인 흐름에서 실패하면 어떤 상태에 빠지는가

### T7. Health Connect 연동

- **대상 파일**: `data/health/` 전체 (HealthConnectManager, HealthConnectRepositoryImpl,
  StayPointDetectorImpl), `domain/health/` 전체, `domain/usecase/GetHealthDataUseCase.kt`,
  `presentation/health/` 전체
- **체크리스트**:
  - [ ] Health Connect 미설치/버전 미달 기기에서 어떤 경로로 실패하는가 (크래시 vs 안내)
  - [ ] 권한 일부만 허용된 경우(수면만 허용 등) 데이터 조회가 올바르게 분기되는가
  - [ ] 시간 범위 계산(수면 요약 등)에서 타임존/자정 경계 처리
  - [ ] StayPointDetectorImpl의 알고리즘 경계: 포인트 0~1개, 모든 포인트 동일 좌표,
    시간 역순 입력일 때
  - [ ] HealthViewModel에서 조회 실패가 UI에 전달되는가, 무한 로딩에 빠질 수 있는가

### T8. 권한 처리 흐름

- **대상 파일**: `presentation/permission/` 전체 (UnifiedPermissionHandler,
  LocationPermissionHandler, BackgroundLocationPermissionHandler,
  MicrophonePermissionHandler, LocationPermissionViewModel, PermissionDialog),
  `domain/permission/PermissionState.kt`
- **체크리스트**:
  - [ ] "다시 묻지 않음" 상태를 올바르게 감지하는가
    (shouldShowRequestPermissionRationale의 최초 요청 전 false 케이스 오판)
  - [ ] 백그라운드 위치는 포그라운드 위치 허용 **후** 별도 요청해야 하는데 순서가 보장되는가
  - [ ] 설정 앱에 다녀온 뒤(onResume) 권한 상태가 재확인되는가
  - [ ] 권한 거부 시 후속 기능(위치 수집, 녹음)이 어떤 상태에 빠지는지 —
    거부인데 스케줄만 등록되는 반쪽 상태가 가능한가
  - [ ] Android 버전 분기(API 29/30/33)의 조건이 올바른가

### T9. 홈/히스토리/온보딩 화면

- **대상 파일**: `presentation/home/` 전체, `presentation/history/` 전체,
  `presentation/onboarding/` 전체, `presentation/model/HistoryUiState.kt`
- **체크리스트**:
  - [ ] HomeViewModel 초기 로딩 실패 시 재시도 수단이 있는가, 에러가 삼켜지는가
  - [ ] 히스토리 목록 → 상세 진입 시 전달 파라미터(id 등)의 null/유효성 처리
  - [ ] 날짜/시간 표시에서 타임존 처리 (서버 UTC ↔ 로컬 표시)
  - [ ] 온보딩 완료 플래그 저장 시점 — 마지막 단계에서 앱이 죽으면 재실행 시 어디부터인가
  - [ ] 화면 회전/프로세스 복원 시 UiState가 초기화되어 재요청이 발생하는가

### T10. 서버 — 인증/JWT

- **대상 파일**: `auth/` 전체 (controller, service, jwt, filter, repository 등),
  `common/config/`의 Security 관련 설정 클래스
  (경로는 `server/src/main/java/com/example/echo/` 기준)
- **체크리스트**:
  - [ ] JWT 만료/서명 오류가 401로 구분되어 내려가는가 (500으로 새는 경로)
  - [ ] refresh 토큰 재사용 감지/무효화가 있는가, 로그아웃 후 기존 토큰이 살아있는가
  - [ ] 필터 체인에서 인증 예외가 GlobalExceptionHandler에 도달하는가
  - [ ] 비밀번호 해싱, 사용자 열거(존재하지 않는 이메일과 비밀번호 오류의 응답 차이)

### T11. 서버 — 대화/AI/음성 파이프라인

- **대상 파일**: `conversation/`, `ai/`, `voice/`, `prompt/` 패키지
  (경로는 `server/src/main/java/com/example/echo/` 기준)
- **체크리스트**:
  - [ ] AI 클라이언트 호출의 타임아웃/재시도 설정, 실패 시 클라이언트에 주는 응답
  - [ ] 대화 상태(진행 중 세션)의 동시 요청 처리 — 같은 사용자의 중복 요청
  - [ ] 업로드된 음성 파일의 크기/형식 검증, 임시 파일 정리
  - [ ] 프롬프트 조립 시 사용자 입력이 그대로 삽입되는가 (프롬프트 인젝션 여지)
  - [ ] 트랜잭션 경계 — 외부 API 호출이 트랜잭션 안에 있는가 (커넥션 고갈)

### T12. 결과 취합 (모든 태스크 완료 후 수행)

- **선행 조건**: T1~T11의 결과 파일이 모두 `docs/bug-hunt-results/`에 존재할 것.
  하나라도 없으면 취합하지 말고 누락된 태스크 ID를 보고하고 중단한다.
- **수행 내용**:
  1. `docs/bug-hunt-results/T1.md` ~ `T11.md`를 모두 읽는다
  2. `docs/bug-hunt-results/SUMMARY.md`를 작성한다:
     - 심각도 "상" 발견 전체 목록 (태스크 ID, 요약, 파일:줄번호)
     - 심각도 "중" 발견 전체 목록 (동일 형식)
     - `[요검증]` 표시 발견 목록 (사람이 재현 검증할 대상)
     - 태스크별 발견 수 집계표
  3. 발견 내용을 재해석하거나 심각도를 바꾸지 않는다 — 원본 보고를 그대로 옮긴다

---

## 우선순위와 진행 순서

사용자 영향이 크고 기존 탐색 영역과 인접한 순서:

1. **T3 (네트워크/토큰)** — 모든 기능의 기반, 버그 시 전면 장애
2. **T1 (음성 녹음)** — 핵심 기능, 리소스/동시성 버그가 흔한 영역
3. **T5 (부팅/업데이트 복구)** — 기존에 고친 알람/위치 버그와 직결
4. **T8 (권한)** — 위치/마이크 기능의 전제 조건
5. **T10, T11 (서버)** — 클라이언트 버그와 대칭 확인 (T3 결과와 대조 가치)
6. T2, T4, T6, T7, T9 — 순서 무관
7. **T12 (취합)** — 반드시 마지막

## 완료 기준

- T1~T11 태스크당 결과 파일 1개가 `docs/bug-hunt-results/`에 존재하고,
  체크리스트 항목별 판정이 기록되어 있음
- 모든 발견에 파일:줄번호와 구체적 재현 시나리오가 포함됨
- T12의 `SUMMARY.md`가 존재하며, 사람은 이 요약(특히 심각도 "상"과 요검증
  목록)을 기준으로 후속 조치를 결정
