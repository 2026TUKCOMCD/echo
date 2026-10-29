# Android 코드 점검 후속 과제 (2026-10-15)

`chore/android-code-health` 브랜치(develop 대상 PR)에서 앱 전체를 점검하며 찾은 문제 중,
**결정이 필요하거나 실기기 확인이 필요해 이번에 고치지 않은 항목**을 모은 문서다.
이번 PR에서 고친 13개는 PR 본문을 본다.

- 확인 수준: ✅ 코드 경로를 직접 확인함 / 🔎 추정 (일부만 확인, 실행 검증 필요)
- 영역: 다른 팀원이 주로 작업한 코드는 "(상의 필요)"로 표시

---

## 0. 다음에 바로 할 일 (순서)

1. ~~**전체 빌드**~~ - 완료(2026-10-15): `./gradlew build` 통과
   (단위 테스트 localDebug·prodDebug 각 310개 통과, lint 오류 0, 모든 변형 컴파일).
   메모리가 부족하면 `--max-workers=2`로 동시 작업을 줄여 실행.
2. **이 PR 실기기 확인** (아래 1장 체크리스트) - 단위 테스트로는 로직만 확인됨.
3. **보류 항목 결정** (2장): 높음 → 중간 → 낮음 순서로 하나씩.

## 1. 이 PR 실기기 확인 체크리스트

- [ ] AI 질문 뒤 **1분 넘게 조용히 있다가** 말하기 → 인식되는지 (60초 뒤 마이크가 꺼지던 문제)
- [ ] **1분 넘게 계속** 말하기 → 60초에서 끊겨 전송되고 대화가 이어지는지
- [ ] 응답을 기다리는 중("처리 중...") **홈으로 나가기** → 소리가 나지 않고, 돌아오면 "말씀해 주세요"로 재개되는지
- [ ] AI가 말을 끝낸 직후(1초 안) 홈으로 나가기 → 상태바 마이크 사용 표시(초록 점)가 뜨지 않는지
- [ ] 일기 탭 → 설정 탭 → **로그아웃 → 다른 계정 로그인** → 일기 탭: 이전 계정 대화가 보이지 않는지
- [ ] 앱을 켜 둔 채 **자정을 넘겨** 다시 열기 → 홈 날짜가 오늘인지
- [ ] Health Connect에서 **심박수만 끄고** 대화 시작 → 서버 로그에 수면·걸음 값이 오는지
- [ ] 기침·TV 소리로 "잘 못 들었어요" 턴이 생긴 뒤 대화 상세에 **빈 말풍선**이 없는지
- [ ] (H1) 종료 다이얼로그에 **"응"이라고 말한 뒤 "마치기"** → 몇 초 "처리 중..." 뒤 작별 화면으로 끝나는지 (AI 대답은 재생되지 않음)

---

## 2. 보류 항목

### 높음

#### H1. 종료 다이얼로그를 띄운 채 말하면 "마치기"가 무시됨 ✅ → 처리함 (결정: A 종료 예약)
- 위치: `ConversationViewModel.endConversation()`(Sending이면 `transitionTo` 실패로 return), `onFarewellConfirmed()`,
  `ConversationScreen.BottomActionSection`(Sending이면 종료 버튼 비활성)
- 시나리오: "대화를 마치시겠어요?"에 소리 내어 "응, 그만할게"라고 답함 → VAD가 발화로 보내 Sending
  → "마치기"를 눌러도 다이얼로그만 닫히고 대화가 계속됨 → `/end` 미호출(일기 미생성), 마이크·화면 켜진 채
- 검토한 선택지: A. Sending 중 종료 요청을 기억했다가 응답이 오면 재생 없이 종료 / B. 다이얼로그가 열려 있는 동안 VAD 정지 /
  C. Sending 중에는 "마치기" 비활성 + 안내 문구
- **처리(A)**: 응답을 기다리는 중 "마치기"를 누르면 녹음을 바로 멈추고 종료를 예약 → 응답이 오면 재생하지 않고 받은 만큼 저장한 뒤 종료,
  메시지가 실패해도 종료. 종료 요청 중 다시 누른 종료는 예약하지 않음 (`ConversationViewModel.endConversation()`·`playReply()`)
  - 말로 한 대답("응, 그만할게")은 대화 기록에 남음. 말로 대답해도 반응이 없게 하는 B는 필요하면 A 위에 추가
- 테스트: `ConversationViewModelTest` 3개 (재현 2개는 수정 전 실패 확인)

#### H2. 뒤로가기 연타·대화 종료 직후 back → 두 단계 pop, 탭바 없는 빈 화면 → 처리함 (PR #476)
- 위치: `MainActivity`의 `onBack = { navController.popBackStack() }`(일기 상세·대화 상세),
  `ConversationScreen`의 Ended 2초 뒤 `onBack()`
- 추정 원인: NavHost 기본 전환(700ms 페이드) 동안 나가는 화면이 남아 터치·effect가 살아 있어 한 번 더 pop
  (전환을 따로 지정하지 않아 기본값을 쓰는 것까지는 확인)
- 시나리오: 일기 상세에서 ←를 두 번 → 일기 탭까지 닫힘 / 대화 종료 후 1.3~2초 사이 back → HOME까지 pop
- 제안: `dropUnlessResumed { navController.popBackStack() }` 또는 entry가 RESUMED일 때만 pop
- 확인: 실기기(또는 Compose UI 테스트) 필요
- **처리(#476)**: Robolectric Compose UI 테스트로 재현됨(← 연타 → 홈까지, 종료 직후 back → 백스택이 비어 빈 화면).
  ← 버튼은 `dropUnlessResumed`로 전환 중 호출을 버리고, 대화 화면의 2초 자동 복귀는 `lifecycle.withResumed`로
  화면이 보일 때까지 기다렸다 실행(drop으로 하면 2초 안에 홈으로 나갔다 오면 작별 화면에 갇힘)

#### H3. 로그아웃 후 재부팅·앱 업데이트 시 위치 수집 재개 ✅ (상의 필요: 위치 수집) → 처리함 (결정: A 로그인 여부 확인, PR #477)
- 위치: `BootReceiver.scheduleLocationCollection()`(로그인 여부 확인 없이 `enableLocationCollection`),
  `MorningAlarmReceiver`도 로그인 확인 없음
- 시나리오: 로그아웃(`disableLocationCollection`으로 수집·아침 알람 중지) → 재부팅 → 백그라운드 정밀 GPS 수집 재개,
  아침 알람도 매일 다시 예약
- 제안: 두 receiver에서 로그인 여부(토큰) 또는 "수집 활성" 플래그 확인
- 테스트: Robolectric 가능 (토큰 없이 BOOT_COMPLETED → `enableLocationCollection` 미호출)
- **처리(#477)**: `LocationScheduler.isLoggedIn`(토큰 존재)을 두고 `BootReceiver`·`MorningAlarmReceiver`가 로그아웃 상태면
  위치 수집·아침 알람(·안내 알림)을 건너뜀. 로그인한 사용자 동작은 그대로

#### H4. Android 9~13(API 28~33)에서 Health Connect 권한을 줄 수 없음 ✅(코드) → 처리함 (PR #478)
- 위치: `UnifiedPermissionHandler`의 `healthConnectLauncher = RequestMultiplePermissions()`
  - 건강 권한은 API 34부터 플랫폼 권한이라 33 이하에서는 화면 없이 바로 거부됨
  - manifest에 API 33 이하 Health Connect 앱용 권한 이유 화면(`androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`)이 없음
    (API 34+용 `VIEW_PERMISSION_USAGE` alias만 있음)
- 결과: 구형 폰에서는 건강 데이터가 항상 null로 전달되고, 앱이 다시 묻지도 않음
- 제안: `PermissionController.createRequestPermissionResultContract()`로 교체 + manifest activity-alias 추가
  (올바른 contract 코드는 지금 쓰이지 않는 `HealthConnectPermissionHandler`에 있음)
- 확인: Android 13 이하 + Health Connect 앱 설치 기기 필요
- **처리(#478)**: 공식 문서 방식으로 교체 - `PermissionController.createRequestPermissionResultContract()` +
  manifest에 13 이하용 `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` alias. Android 13 이하 실기기·에뮬레이터 확인은 남음

#### H5. 디버그 HTTP 로그에 대화·건강·위치·일기·개인정보 본문이 남음 ✅ (팀 방침) → 처리함 (결정: C, 이 PR)
- 이번 PR에서는 `/api/auth/`(비밀번호·토큰)만 본문 로깅에서 뺌
- 여전히 본문 기록: `/message`·`/tts-retry`(전사·AI 답변), `/start`(건강·위치), `/api/diaries`(일기 본문),
  `/api/users/me/preferences`(생일·주소·집 좌표)
- 실기기에 prodDebug APK를 설치해 쓰므로 logcat(adb·버그 리포트)에 남음
- 검토한 선택지: A. 개인정보 경로만 헤더까지 / B. 전부 헤더까지 / C. 기본 헤더까지 + 필요할 때 본문 로깅 켜기 / D. 현행 유지
- **처리(C)**: 디버그 로그는 기본적으로 모든 요청을 헤더까지만 남기고, 응답 내용을 봐야 할 때만 개발자 PC에서
  `-Pecho.logHttpBody=true`(→ `BuildConfig.LOG_HTTP_BODY`)로 빌드해 본문까지 남김. 켜도 인증 본문은 남기지 않음

### 중간

#### M1. 시작 화면(CHECKING)이 네트워크·서버 오류에도 로그인 화면으로 보냄 ✅ (인증 흐름)
- 위치: `MainActivity`의 `composable(Routes.CHECKING)` - `ApiResult.Error`면 오류 종류와 상관없이 LOGIN (토큰은 남음)
- 시나리오: 오프라인이거나 서버 자동 재배포 중에 앱을 열면 다시 로그인하라고 함 (비밀번호를 기억하기 어려운 사용자에게 치명적)
- 제안: 401/403만 LOGIN, 그 외는 재시도 화면 또는 HOME

#### M2. 회원가입 경로에서 LOGIN이 HOME 아래 남음 ✅
- 백스택: LOGIN → SIGNUP → ONBOARDING(`popUpTo(SIGNUP)`) → HOME(`popUpTo(ONBOARDING)`) = [LOGIN, HOME]
- 시나리오: 가입 직후 홈에서 back → 로그인 화면(토큰은 있음), "두 번 눌러 종료"도 꺼짐(`previousBackStackEntry != null`)
- 제안: `onSignupSuccess`에서 `popUpTo(Routes.LOGIN) { inclusive = true }`, 온보딩 완료에서 `popUpTo(0) { inclusive = true }`

#### M3. 위치 수집 서비스가 시작될 때마다 아침 인사 알림이 소리와 함께 뜸 ✅ (상의 필요: 위치 수집)
- 위치: `LocationCollectionService.onStartCommand`(액션 없는 시작은 전부 "최초 시작" → `showMorningGreetingNotification()`),
  `LocationCollectionService.start()`·`LocationScheduler.enableLocationCollection()`에 실행 중 확인 없음
- 시나리오: 수집 시간대에 대화 화면에 들어가면(`UnifiedPermissionHandler`가 `enableLocationCollection` 호출)
  "☀️ 오늘도 행복한 하루 되세요! 조금 이따 대화 시간에 만나요!"가 뜸 (채널 IMPORTANCE_DEFAULT라 소리 남)
- 제안: 이미 실행 중이면 시작하지 않기, 인사 알림은 아침 알람으로 시작할 때만 + `setOnlyAlertOnce(true)`

#### M4. 서비스 재시작(START_STICKY) 때 권한 확인 없이 위치 포그라운드 서비스 시작 🔎 (상의 필요: 위치 수집)
- 위치: `LocationCollectionService.onStartCommand` - 재시작(intent == null)도 최초 시작과 같이 `startForeground(TYPE_LOCATION)`
- 추정 시나리오: 수집 중 위치 권한을 끄면 프로세스 종료 → 시스템이 서비스 재시작 → API 34+에서 SecurityException
- 제안: intent == null이면 조건·시간대 확인 후 아니면 `stopSelf()` + `START_NOT_STICKY`

#### M5. 쓰지 않는 심박수 권한을 요청함 ✅
- 이번 PR로 심박수를 꺼도 다른 데이터는 읽히게 했지만, 요청 목록에는 그대로 있음
  (`HealthConnectManager.REQUIRED_PERMISSIONS`, `UnifiedPermissionHandler.getHealthConnectPermissions()`, manifest `READ_HEART_RATE`)
- manifest `READ_EXERCISE_ROUTE`는 요청·사용 모두 안 함
- Health Connect 정책상 쓰는 데이터만 요청해야 함 → 앞으로 심박수를 쓸 계획이 없으면 제거

#### M6. 온보딩 "건너뛰기"가 입력값을 지우지 않고 검증도 건너뜀 ✅
- 위치: `OnboardingViewModel.skip()`
- 시나리오: 보호자 이메일에 "abc"를 쓰고 건너뛰기 → '완료'를 누를 때마다 저장 실패만 반복(서버 `@Email`),
  수면 시간 "30"을 쓰고 건너뛰기 → 1~24 검사 없이 저장
- 결정 필요: 건너뛰면 그 칸을 비울지 (+ 로딩 중 버튼 비활성으로 submit 중복 방지)

#### M7. 설정 화면 처음 불러오기 실패를 알리지 않고 다시 시도하지도 않음 ✅
- 위치: `SettingsViewModel` 초기 로드 - 실패하면 `isLoading = false`만
- 시나리오: 오프라인에서 설정에 들어가면 "사용자"/"미설정"만 보이고 앱을 다시 시작할 때까지 그대로
- 제안: 오류 안내 + 화면 ON_RESUME에서 다시 불러오기

#### M8. VAD 모델 로드·추론이 매 턴 메인 스레드에서 실행 ✅(코드, 성능 측정 필요)
- 위치: `VoiceRecordingManager.start()` - 스코프가 `Dispatchers.Main`이라 `vadProcessor.initialize()`(ONNX 모델 로드)와
  프레임마다의 `isSpeech()`가 메인 스레드, `stop()`에서 close하므로 매 턴 모델을 다시 불러옴
- 제안: 저사양 기기에서 프레임 드롭을 먼저 측정 → `initialize()`만 `Dispatchers.Default`로 (리스너 콜백 스레드는 유지)

#### M9. 생년월일·필수 항목 검증이 화면과 서버 규칙과 어긋남 🔎
- 온보딩: 날짜가 무효가 되면 직전 유효값을 그대로 둠(4월에 31일로 고치면 4월 3일로 저장), 연도 범위 검사 없음
- 설정: 생년월일·거주 지역을 비울 수 있지만 서버 `@NotNull`이 400, 반대로 보호자 이메일은 서버가 null을 허용하는데 화면에서 비울 수 없음

#### M10. 메인 스레드에서 Keystore·SQLCipher 초기화 🔎
- `MainActivity` 첫 컴포지션의 `AuthRepository` 기본 인자 → `AppDatabase.getInstance`(비밀번호 생성·`loadLibrary`),
  `UnifiedPermissionHandler`의 `TokenStorage(context).getCurrentUserId()`(새 EncryptedSharedPreferences 생성)
- 첫 실행 저사양 기기 ANR 위험 → StrictMode 계측으로 측정 후 결정

### 낮음

- **L1** 반복 방문 장소 수정 🔎 (상의 필요): 라벨만 바꿔도 요일·시간을 항상 보내 `manualSchedule`이 고정됨,
  시간이 자유 입력이라 "9:00"이면 400, 삭제 연타 시 성공 뒤 실패 스낵바
- **L2** 대화 상세 머리글 날짜·시간이 기기 시간대 기준 ✅ (카드는 KST) -
  `ConversationHistoryDetailViewModel.formatDate/formatTime` → `SessionSummaries`의 `formatKoreanDate/formatKoreanTime` 재사용
- **L3** 배터리 잔량을 못 읽는 기기에서 `Integer.MIN_VALUE`를 저전력으로 판단 ✅(코드) (상의 필요) -
  `LocationScheduler.getBatteryLevel()` → "배터리 부족" 알림이 매시간
- **L4** GPS 요청 취소 안 함(`CancellationTokenSource` 미사용), `onDestroy`에서 `serviceScope` 미취소 🔎 (상의 필요)
- **L5** 스트리밍 응답 본문을 메인 스레드에서 닫을 때 IO 스레드 읽기와 경합 가능 🔎 -
  `AudioPlayerManager.stop()` → `closeQuietly`는 IOException만 잡음
- **L6** 알림 PendingIntent requestCode 0이 겹쳐 다른 알림을 탭해도 권한 다이얼로그 extra가 붙음 🔎
- **L7** 알림 딥링크(`navigate_to=home`)와 CHECKING이 경합해 HOME이 두 번 쌓일 수 있음 🔎
- **L8** 일기 ViewModel이 화면 밖에서도 구독, 세션마다 메시지 전체 재조회(N+1), messages 인덱스 없음 🔎
  (인덱스 추가는 DB 마이그레이션 필요)
- **L9** 위치 권한을 거부한 사용자에게도 매일 06:00 HIGH 알림 - 의도인지 확인 (`MorningAlarmReceiverTest`가 이 동작을 검증 중)
- **L10** manifest `USE_EXACT_ALARM` - Play 정책상 알람·캘린더 앱 전용이라 스토어 배포 시 반려될 수 있음
- **L11** `/end` 실패 분기가 백그라운드에서도 녹음을 다시 시작 ✅ - `ConversationViewModel.endConversation()` Error 분기

### 정리 (코드 삭제·lint) - 범위 결정 필요

- **미사용 대화 컴포넌트**: `ActiveConversationView`(+`CharacterPlayer`), `CharacterAnimationManager`(ExoPlayer), `AiCharacterImage`,
  `RecordingIndicator`, `PulseEffect`, `MicrophoneOffIcon`, `ConversationControls`, `MessageList`, `RippleAnimation`, `VoiceEqualizerAnimation`
  - 함께 빠질 수 있는 것: `res/raw` mp4 6개(약 8.3MB), `wellbot.png`, media3 의존성 (minify가 꺼져 있어 지금은 APK에 전부 포함)
  - 캐릭터 영상을 다시 쓸 계획이 있는지 먼저 확인
- **미사용 ViewModel API·UiState 필드**: `updateVoiceAmplitude`(음량 반응 C안에 쓸 거면 유지), `addUserMessage`/`addAiMessage`,
  `onSpeechUnrecognized`, `onFarewellAnimationFinished`, `ConversationScreen`의 `onLogout`·`onRetryClick` 등.
  처리 타이머가 화면에서 쓰지 않는 필드를 매초 갱신함
- **미사용 권한·건강 파일(약 860줄)**: `LocationPermissionHandler`, `BackgroundLocationPermissionHandler`, `MicrophonePermissionHandler`,
  `LocationPermissionViewModel`, `HealthConnectPermissionHandler`(H4의 contract 코드는 보존), `HealthViewModel`(+`HealthViewModelTest`),
  `PermissionState`, `LocationException`
- **기타 미사용**: `VoiceSettingsDialog` 공개 버전·`VoiceSettingsViewModel`, `MessageMapper`, `UserApi.getUser/updateUser`, `DiaryApi.getDiary`,
  일부 DAO 메서드, `Color.kt` 일부 색
- **lint**: `ObsoleteSdkInt` 8곳(minSdk 28이라 항상 참인 버전 분기), 미사용 리소스(`img_2.png` 375KB, `conversation_character.png` 185KB,
  템플릿 색), `MainActivity`의 쓰지 않는 `findStartDestination` import
