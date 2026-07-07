# 버그 수정 계획 (Haiku 수행용)

## 배경

`docs/bug-hunt-results/T*.md`의 탐색 결과 중, **검증(Fable 5 교차검증)을 통과한
심각도 상·중 발견만** 이 계획의 수정 대상이다. 심각도 "하", 기각된 발견,
dead code 정리 항목은 제외한다.

각 결과 파일에는 haiku가 처음 쓴 원본 보고 아래에 **"## 검증 노트"** 섹션이
있다. **수정 전 반드시 해당 발견의 검증 노트를 읽어라** — 원본 보고의 메커니즘
설명이나 심각도가 검증에서 정정된 경우가 많다(예: T11의 "타임아웃 1초"는 사실
오류였다). 이 계획서의 서술과 검증 노트를 기준으로 삼고, 원본 보고의 표현은
참고만 한다.

> **이미 수정됨(제외)**: T3 발견 #1(refresh 실패 시 토큰 오삭제)은 PR #354로
> 수정 완료. 이 계획에 포함하지 않는다.

## 수행 규칙 (모든 수정 공통)

1. **한 세션에 하나의 수정(F번호)만** 수행한다.
2. **브랜치·커밋·PR (T3 수정과 동일한 방식)**:
   - `origin/develop`에서 새 브랜치를 판다:
     `git fetch origin && git checkout -b fix/<슬러그> origin/develop`
   - 수정 + 테스트만 스테이징한다. **`docs/`는 커밋에 포함하지 않는다.**
   - 커밋 메시지: 제목 `fix(android): ...` 또는 `fix(server): ...`,
     본문에 문제와 해결을 요약. 마지막 줄에
     `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`
   - `git push -u origin <브랜치>` 후
     `gh pr create --base develop --title ... --body ...`
   - PR 본문 끝: `🤖 Generated with [Claude Code](https://claude.com/claude-code)`
3. **최소 변경 원칙**: 발견된 문제만 고친다. 주변 리팩터링·스타일 변경 금지.
   기존 동작(정상 경로)은 보존한다.
4. **검증 (필수)**:
   - **로직이 단위 테스트 가능하면 테스트를 반드시 추가하고 통과시킨다.**
   - Android: `./gradlew testLocalDebugUnitTest --tests "<클래스 FQN>"`
     - 주의: 이 프로젝트는 product flavor가 `local`/`prod`로 나뉜다.
       `testDebugUnitTest`는 ambiguous 에러가 난다. 반드시 `testLocalDebugUnitTest`.
     - 테스트 라이브러리: JUnit4, MockK, Robolectric, kotlinx-coroutines-test 사용 가능.
   - Server: `./gradlew test --tests "<클래스 FQN>"`
   - **UI/설정처럼 단위 테스트가 비현실적인 항목**(권한 화면, Feign 설정 등)은
     테스트 대신 **빌드 통과(`./gradlew compileLocalDebugKotlin` 또는
     서버 `./gradlew compileJava`)** + "왜 테스트가 어려운지"와 "어떻게 수동
     확인했는지"를 PR 본문에 서술한다. 각 F 항목에 어느 쪽인지 명시해 둔다.
5. **파일:줄번호는 참고값**이다. 수정 전 실제 파일을 열어 현재 줄 위치를 다시 확인한다.
6. 수정을 끝내면 해당 F 항목을 이 문서 하단 "진행 체크리스트"에서 완료 표시할 것.

## 심각도

- **상**: 크래시, 데이터 유실/오염, 보안 취약점, 기능 전체 불능
- **중**: 특정 조건 오동작, 리소스 누수, 잘못된 데이터

---

## 수정 항목

우선순위: 보안·전면장애 → 리소스 누수 → 기능 오동작 순.

### F1. [상] (서버) refresh 토큰이 access 토큰으로 사용 가능 + 로그아웃 후에도 유효 — **보류(HOLD)**

- **상태**: **이 계획에서 수정 보류.** 아래 선결 확인 전까지 손대지 말 것.
- **보류 사유**: `server/CLAUDE.md`는 "인증: MVP 단계로 고정 userId=1 사용
  (CurrentUserArgumentResolver)"이라고 하는데, 실제 코드는 JWT 인증이 동작하는
  정식 구현이다(SecurityContext에서 userId 추출). 문서와 코드가 어긋나 있어,
  이 서버가 지금 JWT 인증을 실제로 쓰는지 아니면 아직 userId=1 고정 단계인지
  불확실하다. 이 상태에서 토큰 타입 검증을 얹으면 헛수정이 될 수 있다.
- **선결 확인(사람 담당)**: 팀에 "현재 JWT 인증을 실제 사용 중인가"를 확인한다.
  - 실제 사용 중이면 → 이 항목을 활성화하고 아래 참고 방향으로 수정한다.
  - 아직 userId=1 고정이면 → 인증 정식 도입 시점까지 이 발견은 유효하지 않다.
- **(활성화 시) 참고 수정 방향**: buildToken에 `type`(access/refresh) 클레임 추가 →
  JwtAuthFilter는 `type==access`만 인증에 사용 → AuthService.refresh는
  `type==refresh`만 허용. 상세 근거는 `docs/bug-hunt-results/T10.md` 검증 노트 참조.
- **haiku 지시**: 이 항목은 **수행하지 말 것.** 선결 확인이 끝났다는 별도
  지시가 있기 전까지 F2부터 시작한다.

### F2. [중 · 부하 시 상] (서버) @Transactional 안에서 외부 API 호출 → DB 커넥션 고갈

- **출처**: `docs/bug-hunt-results/T11.md` 발견 #3 (검증 노트: 이번 태스크 실질 1순위)
- **파일**: `server/src/main/java/com/example/echo/conversation/service/ConversationService.java`
  - `startConversation` (37행 `@Transactional`)
  - `processUserMessage` (67행 `@Transactional`, 내부 73/78/81행에서 STT→OpenAI→TTS)
  - `endConversation` (110행 `@Transactional`)
- **문제**: 트랜잭션 경계 안에서 수 초~수십 초 걸리는 외부 API를 호출해, 그
  시간 동안 DB 커넥션을 점유한다. 특히 `processUserMessage`는 컨텍스트를
  인메모리(ContextService의 ConcurrentHashMap)에서 읽고 히스토리도 인메모리
  리스트에 add할 뿐 **DB 작업이 없는데도** 커넥션을 잡는다. 동시 사용자가 풀
  크기를 넘으면 `Connection is not available`로 대화 API가 마비된다.
- **수정 방향**: **먼저 각 메서드가 실제로 어떤 DB 쓰기를 하는지 조사**하라
  (호출하는 service들이 JPA 저장을 하는지). 그 결과에 따라:
  - DB 작업이 없으면 → 해당 메서드의 `@Transactional` 제거.
  - DB 작업이 있으면 → 외부 API 호출을 트랜잭션 밖으로 분리(읽기/외부호출/쓰기
    단계 분리), 또는 트랜잭션 범위를 DB 접근 구간으로 좁힌다.
  - **주의**: 조사 없이 무조건 제거하지 말 것. 조사 근거를 PR에 남길 것.
- **검증**: 순수 트랜잭션 경계 변경은 단위 테스트가 어렵다 → **빌드 통과 +
  "각 메서드의 DB 작업 유무 조사 결과"를 PR에 서술**. 기존 서비스 테스트가 있으면
  함께 통과 확인(`./gradlew test`).

### F3. [중] (서버) OpenAI/STT Feign 클라이언트에 명시적 타임아웃 없음 (기본 60초로 과도)

- **출처**: `docs/bug-hunt-results/T11.md` 발견 #1 (검증 노트: **"1초"는 사실
  오류. 실제로는 기본 read 60초로 너무 김**)
- **파일**:
  - `server/src/main/java/com/example/echo/voice/config/OpenAIFeignConfig.java`
    (타임아웃 미설정)
  - 참고(올바른 예): `voice/config/AzureTtsFeignConfig.java:27`,
    `voice/config/SupertoneFeignConfig.java:21` — 둘 다
    `new feign.Request.Options(10_000, 30_000)` (connect 10s / read 30s)
- **문제**: OpenAI Chat/Whisper 클라이언트는 타임아웃 미설정이라 Feign 기본값
  (connect 10s / **read 60s**)이 적용된다. 느린 응답이 스레드와(F2와 결합 시)
  DB 커넥션을 최대 60초 붙잡는다.
- **수정 방향**: OpenAIFeignConfig(및 STT/Whisper용 Feign 설정이 별도라면 그곳)에
  Azure/Supertone과 **일관된** `Request.Options` 빈을 추가해 read 상한을 낮춘다
  (예: connect 10s, read 30s — 실제 OpenAI 응답 특성을 고려해 팀과 합의된 값).
  - **처방 주의**: "타임아웃을 늘려라"가 아니다. **상한을 설정해 낮추는 것**이 목적.
- **검증**: 설정 변경이라 단위 테스트 비현실적 → **빌드 통과 + PR에 설정 전/후
  값과 근거 서술**. (선택: FeignClient 설정이 로드되는지 확인하는 스모크 테스트)

### F4. [중 · 트리거 요검증] (서버) conversationHistory가 thread-unsafe한 ArrayList

- **출처**: `docs/bug-hunt-results/T11.md` 발견 #2 (검증 노트: 상→중 하향, 트리거 드묾)
- **파일**:
  - `server/src/main/java/com/example/echo/context/domain/UserContext.java:26`
    (`conversationHistory = new ArrayList<>()`)
  - `server/src/main/java/com/example/echo/context/service/ContextService.java:143`
    (`context.getConversationHistory().add(turn)`)
- **문제**: 같은 userId의 요청이 시간적으로 겹치면(드물지만 재시도·중복 탭 등)
  ArrayList의 비동기화 add로 `ConcurrentModificationException`이나 데이터 손상
  가능. userId별 격리는 ConcurrentHashMap으로 되어 있으나 리스트 자체는 무방비.
- **수정 방향**: `conversationHistory`를 스레드 안전한 리스트로
  (`Collections.synchronizedList(new ArrayList<>())` 또는
  `CopyOnWriteArrayList`) 교체. 읽기 후 순회 지점이 있으면 스냅샷/동기화 블록
  고려. 최소 변경으로.
- **검증 (테스트 가능하면 필수)**: 동일 userId에 대해 여러 스레드가 동시에
  addConversationTurn을 호출해도 손실·예외가 없음을 확인하는 동시성 테스트.
  - 명령: `./gradlew test --tests "com.example.echo.context.*"`

### F5. [중] (Android) 재시작 시 VadSilero 인스턴스가 close 없이 덮어써져 네이티브 리소스 누수

- **출처**: `docs/bug-hunt-results/T1.md` 발견 #1의 검증자 정정판 (검증 노트 참조)
  - **주의**: 원본 보고 #1("초기화 실패 시 누수")은 사실 오류로 기각됨. 실제
    문제는 **초기화 성공 후 재시작** 경로다. 검증 노트를 반드시 읽을 것.
- **파일**:
  - `android/.../data/voice/VadProcessor.kt:35` (`vad = Vad.builder()...` — 기존
    인스턴스 확인 없이 덮어씀)
  - `android/.../data/voice/VoiceRecordingManager.kt:80` (start의 initialize 호출)
- **문제**: 녹음 중 에러로 job이 끝난 뒤 `stop()` 없이 다시 `start()`가 불리면
  (예: ConversationViewModel.startRecording → audioRecordManager.start 직접 호출),
  `initialize()`가 기존 `vad`를 close하지 않고 새 인스턴스로 덮어써 Silero의
  네이티브(ONNX) 리소스가 누적 누수된다.
- **수정 방향**: `VadProcessor.initialize()`에서 새로 만들기 전에 기존 `vad`가
  non-null이면 `close()`한다(멱등 초기화). 또는 이미 초기화됐으면 재초기화를
  건너뛴다. `isInitialized()` 헬퍼가 이미 있으니 활용.
- **검증 (테스트 필수)**: `VadProcessor`는 `com.konovalov.vad` 라이브러리에
  의존하므로 순수 JVM 테스트가 어려울 수 있다. **가능하면** MockK로 Vad 빌더를
  가로채 initialize를 두 번 호출했을 때 이전 인스턴스 close가 호출되는지 검증.
  라이브러리 static/생성자 모킹이 불가하면 → 빌드 통과 + 로직 근거를 PR에 서술.
- **비고**: F6과 발생 경로가 겹친다(둘 다 "stop 없는 재시작"). 별도 PR로 내되
  PR 본문에서 서로 참조할 것.

### F6. [중] (Android) Completed/Error/Processing 상태에서 start() 재호출 시 이전 scope 누수

- **출처**: `docs/bug-hunt-results/T1.md` 발견 #3 (검증: 확증, Processing 상태 보충)
- **파일**: `android/.../data/voice/AudioRecordManager.kt:79-103` (start),
  90행에서 `scope = CoroutineScope(...)`로 덮어씀; 201-215행 observeVadState의
  collector가 StateFlow 수집이라 스스로 끝나지 않음.
- **문제**: start()의 가드가 Preparing/Listening/Recording만 확인하므로
  Completed/Error/**Processing** 상태에서 start()가 통과하고, 이전 scope를
  cancel 없이 덮어쓴다. 이전 scope의 observeVadState collector가 영구 누수되고,
  collector 중복으로 onReady() 중복 호출도 가능.
- **수정 방향**: start() 진입 시 새 scope를 만들기 전에 기존 `scope?.cancel()`을
  호출하거나, 가드 조건을 보강해 활성/처리 중 상태 재진입을 막는다. 기존 `stop()`이
  하는 정리를 재사용할 수 있는지 검토.
- **검증 (테스트 필수)**: `AudioRecordManager`는 내부 생성자 주입
  (`@VisibleForTesting internal constructor(voiceRecordingManager, audioFileManager)`)이
  있어 MockK로 두 의존성을 주입할 수 있다. Completed/Error 상태로 만든 뒤 start()를
  다시 호출했을 때 이전 scope가 취소되는지(또는 재진입이 무시되는지) 검증.
  - 명령: `./gradlew testLocalDebugUnitTest --tests "com.example.graduation_project.data.voice.AudioRecordManagerTest"`

### F7. [중] (Android) 정상 stop()이 CancellationException을 에러로 변환

- **출처**: `docs/bug-hunt-results/T1.md` 검증 노트의 보충 발견
- **파일**: `android/.../data/voice/VoiceRecordingManager.kt:108`
  (`catch (e: Exception)`가 CancellationException까지 잡음)
- **문제**: stop()이 recordingJob을 취소하면 collect 지점에서
  CancellationException이 던져지는데, 이를 `catch (Exception)`이 잡아
  `VadState.Error` + `listener.onError()`로 바꾼다. 즉 정상 종료가 에러 콜백을
  유발한다. 현재 ConversationViewModel(:177-179)이 `wasAudioStoppedForBackground`
  플래그로 백그라운드 경로만 우회 중이다.
- **수정 방향**: Kotlin 코루틴 표준대로 `catch (Exception)`에서
  **CancellationException은 다시 던진다**. 예:
  `catch (e: CancellationException) { throw e }`를 일반 catch보다 위에 두거나,
  `kotlinx.coroutines.CancellationException`을 구분 처리. 근본 수정 후에도
  기존 우회 플래그는 이번 PR에서 건드리지 말 것(범위 최소화; 별도 정리 대상).
- **검증 (테스트 필수)**: start() 후 stop()으로 취소했을 때 listener.onError가
  호출되지 **않는지** 검증하는 테스트. Dispatchers.Main 교체가 필요하면
  기존 `AudioPlayerManagerTest`의 setMain/resetMain 패턴을 참고.
  - 명령: `./gradlew testLocalDebugUnitTest --tests "com.example.graduation_project.data.voice.VoiceRecordingManagerTest"`
  - **주의**: 이 매니저는 AudioRecorder(실제 AudioRecord)와 VadProcessor(native)에
    의존한다. 순수 JVM에서 취소 경로만 태우기 어렵다면, 테스트 가능한 최소
    리팩터가 과하다고 판단될 경우 빌드 통과 + 근거 서술로 대체하고 그 이유를 명시.

### F8. [중] (Android) 설정에서 권한 허용 후 복귀해도 차단 다이얼로그가 사라지지 않음

- **출처**: `docs/bug-hunt-results/T8.md` 발견 #1 (검증 노트: 시나리오 정정 —
  "회수"가 아니라 "허용" 복귀가 문제)
- **파일**: `android/.../presentation/permission/UnifiedPermissionHandler.kt:211-216`
  (마이크 권한 없으면 닫기 버튼 없는 MicrophonePermissionSettingsDialog 표시)
- **문제**: 마이크 거부 상태로 차단형 다이얼로그가 뜬 뒤, 사용자가 설정에서
  마이크를 **허용**하고 돌아와도 권한 체크가 Compose 상태가 아니라 일반 함수
  호출이라 recomposition이 일어나지 않는다. 다이얼로그가 계속 떠 있어 앱을
  강제 종료해야 빠져나온다(어르신 사용자에게 치명적).
- **수정 방향**: `LifecycleEventObserver`로 `ON_RESUME` 시 권한을 다시 읽어
  Compose 상태(mutableState)에 반영해 recomposition을 유발한다. 표준 패턴:
  `DisposableEffect(lifecycleOwner)`에서 observer 등록/해제. 권한 상태를
  `mutableStateOf`로 들고 ON_RESUME마다 갱신.
- **검증**: Compose UI + Activity 생명주기 의존이라 순수 단위 테스트가 어렵다 →
  **빌드 통과 + PR에 "수동 재현/확인 절차"**(거부→다이얼로그→설정 허용→복귀→
  다이얼로그 사라짐)를 단계로 서술. 가능하면 androidTest(계측)로 작성하되
  환경상 어려우면 사유 명시.

### F9. [중] (Android) 대략적 위치(COARSE)만 허용하면 온보딩은 통과하나 위치 수집이 영구 불발 — **보류(HOLD)**

- **상태**: **정책 결정 대기로 보류.** (A)/(B) 중 무엇을 택할지는 위치 수집
  기능이 요구하는 좌표 정확도에 달렸고, 이는 팀이 정할 사안이다. 결정 전까지
  코드를 수정하지 말 것. 아래는 참고용 방향과 트레이드오프다.
  - (A) 온보딩을 FINE 기준으로: COARSE-only일 때 "정확한 위치 필요" 안내/재요청.
    수집 품질(체류지 감지 등) 유지, 현 수집기 설계와 일치. 사용자 단계 1개 추가.
  - (B) 수집기가 COARSE 허용: 사용자 마찰 최소. 단 좌표 정확도 저하로
    지오코딩/체류지 품질 하락 가능.

- **출처**: `docs/bug-hunt-results/T8.md` 검증자 추가 발견
- **파일**:
  - `android/.../presentation/permission/UnifiedPermissionHandler.kt:98-99`
    (`FINE == true || COARSE == true`를 허용으로 판정)
  - `android/.../data/location/LocationScheduler.kt:265-269`
    (checkPrerequisites: FINE 없으면 `COARSE_LOCATION_ONLY` → 수집 미시작)
- **문제**: Android 12+에서 사용자가 "대략적 위치"를 선택하면 온보딩은 허용으로
  간주해 정상 완료되지만, 수집기는 FINE을 요구해 매일 아침 알람이 울려도
  조건 미충족으로 수집이 영영 시작되지 않는다. 사용자는 허용했다고 인식하지만
  기능이 조용히 죽어 있다.
- **수정 방향**: 온보딩의 허용 판정 기준과 수집기 요구 기준을 **일치**시킨다.
  둘 중 정책을 팀이 정해야 하므로 **PR에 선택지를 제시**하되, 코드로는:
  - (A) 온보딩에서 COARSE-only일 때 FINE 재요청 유도 또는 "정확한 위치 필요"
    안내를 띄운다, 또는
  - (B) 수집기가 COARSE도 허용하도록 완화한다(위치 정확도 요구가 낮다면).
  - **주의**: 어느 방향이 맞는지 불명확하면 임의로 정하지 말고, 두 방향의
    트레이드오프를 PR 본문에 적고 리뷰어 판단을 요청한다. (기본 제안: (A) —
    수집 품질을 유지하면서 사용자에게 상태를 알림)
- **검증**: 판정 기준 로직(예: 온보딩 허용 조건 함수)을 순수 함수로 분리할 수
  있으면 단위 테스트. UI 흐름 변경은 빌드 통과 + 수동 확인 절차 서술.

---

## 진행 체크리스트

수정 완료 시 `[x]`로 표시하고 PR 번호를 적는다.

- [ ] F1 — 서버 refresh/access 토큰 타입 구분 (상) — **보류(선결 확인 대기)**
- [x] F2 — 서버 트랜잭션 경계에서 외부 호출 분리 (중) — PR #355
- [x] F3 — 서버 OpenAI/STT Feign 타임아웃 상한 설정 (중) — PR #356
- [x] F4 — 서버 conversationHistory 스레드 안전화 (중) — PR #357
- [x] F5 — Android VadProcessor 재초기화 시 close (중) — PR #358
- [x] F6 — Android AudioRecordManager scope 누수 (중) — PR #359
- [x] F7 — Android VoiceRecordingManager CancellationException 재던지기 (중) — PR #360
- [x] F8 — Android 권한 다이얼로그 onResume 재확인 (중) — PR #361
- [ ] F9 — Android COARSE-only 위치 온보딩/수집 기준 일치 (중) — **보류(정책 결정 대기)**

## 머지 대기 PR과의 관계 (2026-07-07 기준)

develop 대상 열린 PR: #350(설정/알람), #351(Room 암호화), #352(네트워크 설정),
#353(keystore), #354(TokenAuthenticator, 이미 이 작업의 T3 수정).

- **F1~F9와 파일 단위 직접 충돌 없음.** 위 PR들은 이 계획이 건드리는 파일
  (VadProcessor, VoiceRecordingManager, AudioRecordManager, UnifiedPermissionHandler,
  LocationScheduler, JwtProvider/JwtAuthFilter/AuthService, ConversationService,
  OpenAIFeignConfig, UserContext/ContextService)을 건드리지 않는다.
- **주의**: #351이 `android/app/build.gradle.kts`와 `android/gradle/libs.versions.toml`을
  수정한다. 어떤 F 수정에서 **새 테스트 의존성을 추가하려고 이 빌드 파일을
  건드리면** #351과 병합 마찰이 생길 수 있다. Android 테스트는 이미 있는
  의존성(JUnit4/MockK/Robolectric/coroutines-test)만으로 작성하고, 불가피하게
  빌드 파일을 바꿔야 하면 PR에 그 사실을 명시할 것.
- 규칙 2대로 **매번 `git fetch origin` 후 최신 `origin/develop`에서 분기**하면
  위 PR들이 머지돼도 최신 상태 위에서 작업하게 된다.

## 참고

- 각 발견의 상세 근거·시나리오·검증 노트: `docs/bug-hunt-results/T1.md`,
  `T8.md`, `T10.md`, `T11.md`
- 제외된 항목(하/기각/dead code): 각 결과 파일의 검증 노트 "기각"·"정리 목록" 참조
- 미탐색 태스크(T2/T4/T6/T7/T9)와 취합(T12)은 별도 진행. 이 계획은 수정에만 집중.
