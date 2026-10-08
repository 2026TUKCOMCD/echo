# 일기 탭 QA 계획 (2026-10 재점검)

- 작성일: 2026-10-03 (1차 계획 2026-07-21을 대체)
- 대상: `develop` 9009b3d 기준 일기 탭 전체 — 캘린더(`DiaryScreen`), 상세(`DiaryDetailScreen`), 캐시(`DiaryRepository`·Room), 계정 전환, 서버 일기 조회 API
- 범위 밖: 일기 생성 품질(프롬프트 결과의 자연스러움), 일기 그림 생성(별도 계획)
- QA 브랜치: `test/diary-tab-qa` — 이 문서 + 테스트용 리팩터(동작 변경 없음) + 현재 통과하는 테스트만

## 1. 진행 방식

| 구분 | 방법 | 대상 |
|---|---|---|
| 자동 | JVM 단위 테스트 | 날짜 병합·버킷 규칙, 캐시 동기화, 서버 조회 API |
| 수동 | 실기기 체크리스트 (4장) | 캘린더 모양·접근성, 계정 전환, 오프라인, DB 업그레이드 |
| 수정 | 버그마다 `fix/diary-*` 브랜치 | 재현 테스트 먼저(실패 확인) → 수정 → 통과. 재현이 안 되면 그 묶음은 취소하고 이 문서에 "문제없음"으로 기록 |

- 실패하는 재현 테스트는 QA 브랜치에 넣지 않는다(`develop` 빌드가 깨지지 않도록 수정과 같은 PR에 넣는다).
- `DiaryViewModel`·`DiaryDetailViewModel`은 테스트를 위해 생성자 주입(기본값)으로 바꿨다. `HomeViewModel`·`SettingsViewModel`·`HealthViewModel`과 같은 형식이고 `Factory`·동작은 그대로다.

## 2. 1차 계획(2026-07-21) 처리 현황

| 항목 | 상태 |
|---|---|
| SKIPPED/FAILED 이중 의미 | 해결 — `DiaryOutcome` (`8f4c691`) |
| 실패 뱃지 + 이전 content UX | 해결 — PR #381 |
| `upsertSuccess` 경합 재시도 | 해결 — `DiaryServiceTest` 경합 테스트 |
| DIARY 템플릿 운영 배포 | 해결 — `application-prod.yaml`의 `sql.init.mode: always` |
| 날짜 버킷 자정 경계 | 해결 — `389934b` (diaryDate 링크 우선) |
| Room 마이그레이션 | 부분 — DB v5, `AppDatabaseMigrationTest`가 3→4·4→5 검증. 2→3은 테스트 없음(4장 수동 확인) |
| `refresh()` 오프라인 | 이번에 테스트 추가 (`DiaryRepositoryTest`, `DiaryViewModelTest`) |
| `history_detail` 재사용 | 4장 수동 확인 |

## 3. 자동 테스트 (이 브랜치에서 추가, 모두 통과)

**앱**
- `DiaryViewModelTest` (7): 일기 있는 날=HasDiary(세션 수 포함) / 세션만=HasSessions / 없음=Empty, diaryDate 링크가 종료 시각보다 우선, 링크 없으면 종료 시각 KST, 메시지 없는 세션은 Empty, 동기화 실패 시 캐시 유지+오류 노출→재시도 성공 시 해제, 다음 달 이동 시 그 달 동기화·표시
- `DiaryDetailViewModelTest` (2): 그날 일기 + 같은 버킷 세션만(링크 반영), 일기 없는 날
- `DiaryRepositoryTest` (3): 달 범위(윤년 2월 포함) 요청·캐시 저장, 실패 시 캐시 미변경, 최근 N일 동기화

**서버**
- `DiaryControllerTest` (4): 범위 조회(FAILED 포함 필드), 기본 days=30, 역순 범위 400, 남의 일기/없는 일기 404

## 4. 실기기 수동 체크리스트

### 4.1 캘린더
- [ ] 일기 있는 날 탭 → 일기 상세, 세션 1개인 날 → 대화 상세, 2개 이상 → 바텀시트 → 선택 시 대화 상세
- [ ] 아무 기록 없는 날은 눌리지 않음
- [ ] 이전/다음 달 이동, 달마다 첫 요일 위치가 맞음(일요일 시작)
- [ ] 오늘 날짜 강조가 맞음 (자정 전후로 화면을 켜 둔 채 확인)
- [ ] 상태 색(일기/대화만/갱신 실패/생성 실패)이 실제 상태와 맞음
- [ ] 큰 글씨 설정·TalkBack에서 날짜와 상태를 알아볼 수 있음

### 4.2 상세
- [ ] 일기 본문, 실패 시 안내 카드(갱신 실패=이전 내용 유지, 생성 실패=본문 없음)
- [ ] "그날의 대화" 세션 탭 → `history_detail/{conversationId}` 말풍선 화면
- [ ] 자정 직전(23:5x)에 끝난 대화가 서버 일기 날짜와 같은 날에 보임

### 4.3 동기화·오프라인
- [ ] 비행기 모드로 일기 탭 진입 → 캐시 표시 + "일기 동기화 실패 … (눌러서 다시 시도)" → 네트워크 복구 후 눌러서 해제
- [ ] 대화 종료 직후 일기 탭에서 오늘 일기가 갱신됨

### 4.4 계정·데이터
- [ ] A 계정 로그아웃 → B 계정 로그인: A의 일기·대화가 보이지 않음 → 다시 A로 로그인하면 A의 대화가 보임
- [ ] 설정의 체험 데이터 초기화 후 일기·대화가 비워짐
- [ ] 이전 버전(DB v4 이하) 앱에서 업데이트 설치 → 기존 대화 보존, 일기는 다시 동기화됨

## 5. 코드 리뷰 의심점과 처리 계획

재현 전이라 모두 "의심" 단계다. 각 fix 브랜치에서 재현 테스트로 확정한다.

| 묶음 | # | 의심 내용 | 위치 | 처리 |
|---|---|---|---|---|
| 1 | ⑤ | 상태를 6dp 색 점으로만 표시(WCAG 1.4.1 색만으로 정보 전달), 셀 `contentDescription` 없음, 범례 12sp | `DiaryScreen.CalendarDayCell`·`CalendarLegend` | `fix/diary-calendar-accessibility` — 표시 방식은 착수 시 결정 |
| 2 | ⑦ | 달을 빠르게 넘기면 이전 달 요청 결과가 `syncError`를 덮어씀 | `DiaryViewModel.refreshCurrentMonth` | `fix/diary-sync-error` — 재현 테스트 4개로 확정. 가장 마지막 요청 결과만 배너에 반영(요청은 취소하지 않아 캐시 저장 유지), 달 이동 시 배너 즉시 초기화 |
| 2 | ⑥ | 동기화 실패 배너 문구 — 확인 결과 `ApiException`의 한국어 안내문("네트워크 연결을 확인해주세요" 등)이 표시되어 예외 원문 노출은 아님. 문구 다듬기만 검토 | `DiaryScreen` | 묶음 2에서 처리 — 원인 문구를 2종(인터넷 끊김/그 외)으로 단순화(HTTP 코드는 로그만), 기본 테마 글씨 대비 2.55→4.76(`#C62828`), 16sp·높이 48dp 이상·TalkBack 버튼, 문장별 줄 나눔 |
| 3 | ① | 서버에서 지워진 일기(체험 데이터 초기화 등)가 다른 기기 캐시에 남음 — `upsertAll`만 하고 달 범위를 교체하지 않음 | `DiaryRepository.refreshMonth` | **재현 안 됨(2026-10-05)** — 근거가 된 체험 데이터 초기화(7739b3d)는 `16bd5bb`(2026-09-18)에서 팀 합의로 제거됨. 현재 서버에는 일기 삭제 경로가 없음(API·서비스는 생성/갱신만, `DiaryRepository.deleteByUserId`는 호출처 없음, `data.sql`은 프롬프트만 삭제, `ddl-auto: update`, 회원 탈퇴 없음). 달 범위 교체는 대화 종료 직후 `refresh()`로 저장된 일기를 늦게 도착한 달 응답이 지울 수 있어 도입하지 않음. **일기 삭제 기능이 다시 생기면** 범위 교체(+요청 순서 보호) 또는 서버 삭제 기록(tombstone)을 함께 설계할 것 |
| 4 | ③ | 상세 화면이 일기를 `getByDate`로 한 번만 읽어 열려 있는 동안 갱신이 반영되지 않음 | `DiaryDetailViewModel.load` | `fix/diary-stale-display` |
| 4 | ④ | 오늘 표시가 기기 시간대 기준(`LocalDate.now()`)이고 자정이 지나도 갱신되지 않음 | `DiaryScreen.CalendarGrid` | `fix/diary-stale-display` |
| 보류 | ② | 로그아웃 순간 진행 중이던 동기화가 `deleteAll()` 뒤에 끝나면 이전 계정 일기가 다시 저장될 수 있음(`diaries`에 userId 없음) | `AuthRepository.clearOtherAccountCache` | 알려진 위험으로 기록. 근본 수정은 DB v6(userId 컬럼) 필요 |
| 보류 | ⑧ | 서버 `days<=0`이면 "endDate가 startDate보다 이전" 메시지로 400, `days`·범위 상한 없음, 날짜 하나만 오면 조용히 `days`로 조회 | `DiaryService.getDiaries`, `DiaryController` | 앱은 올바른 값만 보내므로 기록만 |

진행 순서: 이 브랜치 PR → 묶음 1 → 2 → 3 → 4.

## 6. 완료 기준

- 3장 자동 테스트가 `develop`에 머지됨
- 4장 체크리스트를 실기기에서 모두 확인(결과를 이 문서에 표시)
- 5장 묶음 1~4가 각각 머지되거나 "재현 안 됨"으로 기록됨
