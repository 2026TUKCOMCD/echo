# APK 업데이트 실패("기존 앱 삭제 후에만 설치 가능") 해결 계획

> **실행 대상**: Claude Haiku (또는 다른 작업자)
> **작성일**: 2026-07-06
> **작업 디렉토리**: `C:\Users\PC\AndroidStudioProjects\echo\android`
> **원칙**: 각 Phase를 순서대로 실행하고, 분기 조건에 맞는 케이스만 진행한다. 확신이 없으면 멈추고 사용자에게 보고한다.

---

## 1. 증상 및 확정된 사실

**증상**: 실기기에 설치된 앱 위에 새 APK를 덮어 설치(업데이트)하면 오류가 떠서, 매번 기존 앱을 삭제한 후에 설치해야 한다.

사용자 인터뷰로 확정된 사실:

| 항목 | 확인된 내용 |
|---|---|
| 오류 문구 | 기기에서 "앱이 설치되지 않았습니다" 일반 팝업만 확인함 (서명 충돌은 **추정**이었고, 정확한 오류 코드는 미확보) |
| 설치 방법 | APK 파일을 기기로 직접 전달하여 설치 (adb/Android Studio 사용 안 함) |
| 전달 APK | 항상 `app-prod-debug.apk` |
| 빌드 출처 | 항상 같은 PC에서 빌드. 단, **Android Studio 재설치 또는 계정 변경 이력 있음** |
| 발생 기기 | 실기기 (에뮬레이터 아님) |
| 발생 빈도 | 매번 발생했으나, 최근 소프트웨어 업데이트 이후로는 **재현되지 않는 것 같다**는 사용자 증언 |

**유력 가설 (우선 검증 대상)**: debug 서명 키는 PC의 `%USERPROFILE%\.android\debug.keystore`에 자동 생성되는데, **Android Studio 재설치/계정 변경으로 이 파일이 재생성되면 같은 PC여도 서명이 달라진다.** 시나리오: 키 재생성 → 기기에 구(舊) 키로 서명된 앱이 남아 있어 덮어 설치가 매번 실패 → 삭제 후 재설치를 반복하는 동안 기기의 앱이 신(新) 키 서명으로 교체됨 → 신 키끼리는 정상 업데이트되므로 "요즘은 되는 것 같다"로 자연 해소. 이 가설이 맞다면 **다음 Android Studio 재설치·PC 포맷·팀원 PC 빌드 시 반드시 재발**하므로, 증상이 사라졌더라도 재발 방지 조치(Case A의 공유 keystore)는 필요하다.

**⚠️ 그래도 Phase 0부터 시작할 것**: 위는 어디까지나 가설이다. 실기기 팝업은 원인을 알려주지 않는 일반 메시지이므로, 추측으로 고치지 말고 반드시 진단으로 확정한다.

**관련 프로젝트 사실** (이미 코드에서 확인됨):
- `app/build.gradle.kts`에 `signingConfigs` 블록이 **없다** → debug는 PC별 자동 생성 keystore로 서명되고, release는 **서명 없이(unsigned)** 생성된다.
- `versionCode = 1` 고정.
- applicationId는 local/prod 플레이버 모두 `com.example.graduation_project`로 동일.
- CI(`.github/workflows/deploy.yml`)는 서버 전용이며 APK를 빌드하지 않는다.

---

## 2. Phase 0 — 진단: 정확한 실패 코드 확보 (필수, 건너뛰기 금지)

**목표**: `INSTALL_FAILED_*` 코드를 확보한다.

1. 오류가 나는 실기기를 USB로 PC에 연결한다 (개발자 모드 + USB 디버깅 켜기).
2. 기기 인식 확인:
   ```
   %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe devices
   ```
   `device` 상태로 잡히지 않으면 멈추고 사용자에게 USB 디버깅 승인을 요청한다.
3. 문제가 재현되는 APK를 빌드한다:
   ```
   cd C:\Users\PC\AndroidStudioProjects\echo\android
   gradlew.bat :app:assembleProdDebug
   ```
   산출물: `app\build\outputs\apk\prod\debug\app-prod-debug.apk`
4. **삭제하지 말고** 그대로 덮어 설치를 시도하여 오류 코드를 받는다:
   ```
   adb install -r app\build\outputs\apk\prod\debug\app-prod-debug.apk
   ```
5. 출력에 나온 `INSTALL_FAILED_*` 코드를 기록하고, 아래 분기표에 따라 해당 Phase로 이동한다.

| 오류 코드 | 의미 | 이동할 곳 |
|---|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 또는 `...INCONSISTENT_CERTIFICATES` | 서명 불일치 | Phase 1 → Case A |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | 기기의 앱이 더 높은 versionCode | Case B |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | 저장공간 부족 | Case C |
| 설치가 **성공**함 | 아래 6번으로 | 6번 절차 후 Case D 또는 Case E |
| 그 외 코드 | 미분류 | 코드를 기록하고 사용자에게 보고 후 지시 대기 |

6. adb 설치가 성공했다면, 같은 APK를 **파일 전달 방식**(실제 사용자 시나리오: 카톡/드라이브로 옮겨 기기에서 탭)으로도 덮어 설치를 시도한다.
   - 파일 설치도 성공 → **Case E** (증상 소멸: 유력 가설대로 자연 해소된 상태. 재발 방지 조치만 진행)
   - 파일 설치만 실패 → **Case D** (전달/설치 UI 경로 문제)

---

## 3. Phase 1 — 서명 비교 (Case A로 분기된 경우)

**목표**: 기기에 설치된 앱과 새 APK의 서명 지문(SHA-256)을 비교하여 불일치를 물증으로 확인한다.

1. 새 APK의 서명 지문:
   ```
   %LOCALAPPDATA%\Android\Sdk\build-tools\<설치된 최신 버전>\apksigner.bat verify --print-certs app\build\outputs\apk\prod\debug\app-prod-debug.apk
   ```
   (`build-tools` 폴더 안에서 가장 높은 버전 디렉토리를 사용한다.)
2. 기기에 설치된 앱의 APK를 꺼내서 같은 방식으로 지문 확인:
   ```
   adb shell pm path com.example.graduation_project
   adb pull <위에서 나온 base.apk 경로> installed.apk
   apksigner.bat verify --print-certs installed.apk
   ```
3. 두 SHA-256 지문을 비교한다.
   - **다르면**: 서명 불일치 확정 → Case A 해결 절차 진행.
   - **같으면**: 서명 문제가 아니다. Phase 0의 오류 코드를 다시 확인하고 사용자에게 보고한다.

---

## 4. 해결 절차 (케이스별)

### Case A — 서명 불일치: 프로젝트 공유 debug keystore 도입 (근본 해결)

**원리**: debug 서명 키는 기본적으로 PC마다 `%USERPROFILE%\.android\debug.keystore`에 자동 생성되어 서로 다르다. PC 포맷·Android Studio 재설치·계정 변경으로도 바뀐다. **프로젝트에 고정 debug keystore를 커밋**해서 어떤 PC에서 빌드해도 같은 키로 서명되게 하는 것이 팀 개발의 표준 관행이다 (Google 공식 샘플들도 사용하는 방식).

1. 저장소 루트가 아닌 **android 디렉토리** 기준으로 keystore 생성 (`android/app/debug.keystore`):
   ```
   keytool -genkeypair -v -keystore app\debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10950 -dname "CN=Android Debug,O=Android,C=US"
   ```
   - `keytool`은 JDK에 포함되어 있다. PATH에 없으면 Android Studio 내장 JBR 사용: `"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"`.
   - 비밀번호/alias는 위 값(표준 debug 규약: `android` / `androiddebugkey`) 그대로 사용한다. **debug 전용 키이므로 비밀이 아니며 git 커밋이 허용된다.**
2. `app/build.gradle.kts`의 `android { }` 블록 안, `buildTypes` **위에** 추가:
   ```kotlin
   signingConfigs {
       getByName("debug") {
           storeFile = file("debug.keystore")
           storePassword = "android"
           keyAlias = "androiddebugkey"
           keyPassword = "android"
       }
   }
   ```
   - `debug` buildType은 자동으로 `signingConfigs.debug`를 사용하므로 buildTypes 쪽 수정은 불필요하다.
   - `.gitignore`에 `*.keystore` 패턴이 있는지 확인하고, 있다면 `!app/debug.keystore` 예외를 추가한다.
3. 빌드 확인:
   ```
   gradlew.bat :app:assembleProdDebug
   apksigner.bat verify --print-certs app\build\outputs\apk\prod\debug\app-prod-debug.apk
   ```
   지문이 새 keystore의 것으로 바뀌었는지 확인한다.
4. **사용자에게 고지할 것**: 키가 바뀌므로 **이번 한 번만** 기기에서 기존 앱을 삭제하고 새 APK를 설치해야 한다. 이후부터는 어떤 PC에서 빌드해도 삭제 없이 업데이트가 가능해진다.
5. 팀원 공유: keystore와 gradle 변경을 커밋하면 팀원 전원이 같은 서명을 쓰게 된다.

**금지 사항**:
- release/업로드용 키를 이 방식으로 커밋하지 말 것 (debug 키만 커밋 허용).
- `%USERPROFILE%\.android\debug.keystore`를 삭제하거나 덮어쓰지 말 것 (다른 프로젝트에 영향).

### Case B — versionCode 다운그레이드

기기에 설치된 앱의 versionCode 확인:
```
adb shell dumpsys package com.example.graduation_project | findstr versionCode
```
`app/build.gradle.kts`의 `versionCode`(현재 1)를 기기 값보다 **크게** 올리고 재빌드한다. 재발 방지는 §5 참고.

### Case C — 저장공간 부족

기기 저장공간을 확인하고 사용자에게 정리를 요청한다. 코드 수정 없음.

### Case D — adb로는 성공하는데 파일 설치 UI로만 실패

원인은 APK 자체가 아니라 전달/설치 경로다. 다음을 순서대로 확인:
1. **Play Protect**: 설치 차단 팝업에서 "무시하고 설치"가 가능한지 확인. Play 스토어 → 프로필 → Play Protect 설정에서 검사 끄기(개발 기기 한정)를 안내한다.
2. **APK 파일 손상**: 카톡 등 메신저는 파일을 변형할 수 있다. 전달 전후 파일 크기/해시를 비교한다:
   ```
   certutil -hashfile app-prod-debug.apk SHA256
   ```
   해시가 다르면 전달 수단을 Google Drive 링크 또는 `adb install -r`로 바꾼다.
3. 위 두 가지가 아니면 기기의 설치 실패 직후 로그 수집:
   ```
   adb logcat -d | findstr /i "PackageInstaller InstallFailed"
   ```

### Case E — 증상 소멸 (adb·파일 설치 모두 성공)

유력 가설(Android Studio 재설치로 인한 debug.keystore 재생성 → 삭제·재설치 반복으로 자연 해소)이 사실상 확인된 상태다. 다음을 수행한다:

1. **원인 소급 확인 (선택)**: 현재 keystore 생성일이 Android Studio 재설치 시점과 일치하는지 확인하면 가설이 굳어진다:
   ```
   keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -storepass android | findstr /i "Creation valid"
   ```
2. **재발 방지 조치를 그대로 진행**: 증상이 사라졌어도 다음 Android Studio 재설치·PC 포맷·팀원 PC 빌드에서 반드시 재발하는 구조이므로, **Case A의 공유 debug keystore 도입(1~3, 5단계)을 동일하게 수행**한다.
   - 단, 이 경우에도 keystore가 새로 바뀌므로 기기에서 **마지막 1회 삭제 후 재설치**가 필요하다는 점을 사용자에게 고지하고 동의를 받는다 (Case A 4단계와 동일).
3. §6 검증 절차로 이동한다.

---

## 5. 재발 방지 (모범 사례 정착)

1. **공유 debug keystore** (Case A 조치)를 develop에 머지하여 팀 전체가 사용.
2. **versionCode 관리 규칙** 도입: 배포용 APK를 만들 때마다 versionCode를 1씩 증가시킨다. (졸업작품 규모에서는 수동 증가로 충분하며, 자동화가 필요하면 `versionCode = (System.currentTimeMillis() / 1000 / 60).toInt()` 같은 시간 기반 방식은 **쓰지 말고** — 빌드마다 달라져 혼란 유발 — git 태그 기반 또는 수동 관리를 권장.)
3. **배포 수단 통일**: 팀원 기기 배포는 가급적 `adb install -r` 또는 신뢰 가능한 파일 공유(Drive)로 통일하고, 배포한 APK의 SHA256을 함께 공유한다.
4. (선택) release 배포가 필요해지는 시점에는 별도 release keystore를 만들고 **git에 커밋하지 않으며**, `local.properties` 또는 환경변수로 경로/비밀번호를 주입한다.

---

## 6. 검증 절차 (완료 기준)

아래가 모두 통과해야 완료로 판정한다:

1. 실기기에 새 서명의 APK를 설치한다 (Case A였다면 최초 1회 삭제 후 설치).
2. 소스 코드를 사소하게 수정(예: 로그 한 줄)하고 재빌드한다.
3. **삭제 없이** 덮어 설치가 성공하는지 확인:
   ```
   adb install -r app\build\outputs\apk\prod\debug\app-prod-debug.apk
   ```
   → `Success` 출력.
4. 같은 APK 파일을 **파일 전달 방식**(실제 사용자 시나리오)으로도 기기에서 덮어 설치하여 성공을 확인한다.
5. 설치 후 앱을 실행하여 정상 부팅 및 서버 통신(홈 화면 로딩)을 확인한다.
   - **주의**: 이 앱은 EncryptedSharedPreferences(Android Keystore)를 사용한다. 앱을 **삭제 후 재설치**하면 Keystore 키는 사라지는데 `allowBackup="true"`로 인해 백업된 prefs가 복원되어 복호화 크래시가 날 수 있다 (과거 `fix/token-storage-crash`, `fix/reinstall-onboarding-permission-bugs` 브랜치에서 유사 이슈 이력 있음). 재설치 후 크래시가 나면 이 원인을 의심하고 별도 이슈로 보고한다 — 이 계획의 범위에는 포함하지 않는다.

---

## 7. 작업 규칙 (Haiku용 가드레일)

- Phase 0을 건너뛰고 바로 코드를 고치지 말 것. 오류 코드 없이는 어떤 수정도 하지 않는다.
- 분기표에 없는 오류 코드가 나오면 **수정 시도 없이** 코드와 전체 출력을 사용자에게 보고한다.
- 기기의 기존 앱 삭제는 Case A 확정 후 최종 전환 시 **1회만** 수행하며, 수행 전 사용자에게 "기기 내 앱 데이터(대화 기록 등)가 삭제된다"는 점을 고지하고 동의를 받는다.
- 새 브랜치(`fix/apk-update-signature` 권장)를 develop에서 분기하여 작업하고, 커밋 전 `gradlew.bat :app:assembleProdDebug` 성공을 확인한다.
- 이 문서 범위 밖의 리팩터링(서명 외 gradle 정리 등)은 하지 않는다.
