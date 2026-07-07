# 버그 탐색·수정 — 남은 작업 (진행 현황과 다음 할 일)

이 문서는 버그 탐색/수정 작업의 **미완료 항목**을 한곳에 모은 인덱스다.
계획·결과·수정의 상세는 아래 문서들에 있다.

- 탐색 계획(전체 태스크 정의): `docs/bug-hunt-plan-haiku.md`
- 탐색 결과 + 검증 노트: `docs/bug-hunt-results/T*.md`
- 수정 계획 + 진행 체크리스트: `docs/fix-plan-haiku.md`

작성 기준일: 2026-07-07.

---

## 1. 지금까지 완료된 것 (요약)

### 탐색 완료 (검증까지)
`docs/bug-hunt-results/`에 결과 존재. 각 파일 하단 "검증 노트"에 교차검증 결과.

| 태스크 | 영역 | 유효 발견(검증 후) |
|--------|------|-------------------|
| T3 | 클라이언트 네트워크/토큰 | 1건(중, 수정됨) + 부수 |
| T1 | 음성 녹음 파이프라인 | 3건(중) |
| T5 | 부팅/업데이트 복구 | 0건(전부 기각) |
| T8 | 권한 처리 흐름 | 2건(중) |
| T10 | 서버 인증/JWT | 1건(상) + 하 다수 |
| T11 | 서버 대화/AI/음성 | 3건(중, 1건 부하 시 상) |

### 수정 완료 (PR 생성됨)
상세: `docs/fix-plan-haiku.md`.

- T3 발견 #1 → PR #354 (**병합됨**)
- F2 트랜잭션 경계 → PR #355
- F3 Feign 타임아웃 → PR #356
- F4 히스토리 스레드 안전화 → PR #357
- F5 VadProcessor 재초기화 누수 → PR #358
- F6 AudioRecordManager scope 누수 → PR #359
- F7 CancellationException 재던지기 → PR #360
- F8 권한 다이얼로그 onResume 재확인 → PR #361

---

## 2. 남은 작업

### 2-1. 미탐색 탐색 태스크 (T2, T4, T6, T7, T9)

아직 탐색하지 않은 영역. 각 태스크의 대상 파일·체크리스트는
`docs/bug-hunt-plan-haiku.md`의 동일 번호 항목에 정의돼 있다. 수행 규칙(읽기 전용,
보고 형식, 심각도 기준, `[요검증]` 표기 등)도 그 문서 상단을 그대로 따른다.

- [ ] **T2. 오디오 재생** — `data/voice/AudioPlayerManager.kt` + domain/voice 재생 상태.
  ConversationViewModel은 재생 호출 5개 함수만 읽음(계획서에 함수명 명시됨).
- [ ] **T4. 로컬 DB (Room)** — `data/local/`(AppDatabase, dao, entity, mapper).
  **주의**: PR #351(Room SQLCipher 암호화)이 AppDatabase를 바꾸는 중이므로,
  탐색 전 #351 병합 여부를 확인하고 최신 코드 기준으로 볼 것.
- [ ] **T6. 인증 화면(로그인/회원가입)** — `presentation/auth/` + AuthRepository/AuthModels.
  토큰 갱신 동시성은 T3 소관이므로 중복 보고 금지(계획서 범위 주의 참조).
- [ ] **T7. Health Connect 연동** — `data/health/`, `domain/health/`,
  `domain/usecase/GetHealthDataUseCase.kt`, `presentation/health/`.
- [ ] **T9. 홈/히스토리/온보딩 화면** — `presentation/home/`, `history/`, `onboarding/`,
  `presentation/model/HistoryUiState.kt`.

수행 방식: 지금까지처럼 haiku로 태스크당 1세션 실행 → **결과를 사람(또는 상위
모델)이 교차검증**해 `docs/bug-hunt-results/T{2,4,6,7,9}.md` 하단에 검증 노트 추가.
haiku 단독 결과는 오탐·심각도 오판이 잦으므로 검증 단계를 반드시 거친다.

### 2-2. 취합 (T12)

- [ ] **T12. 결과 취합** — `docs/bug-hunt-plan-haiku.md`의 T12 정의를 따름.
  - **선행 조건**: T1~T11 결과가 모두 존재할 것. 현재 미존재: **T2, T4, T6, T7, T9**.
    이들이 채워지기 전에는 취합하지 말 것(누락 태스크를 보고하고 중단).
  - 산출물: `docs/bug-hunt-results/SUMMARY.md`
    - 심각도 "상" 발견 전체 목록(태스크ID·요약·파일:줄)
    - 심각도 "중" 발견 전체 목록
    - `[요검증]` 발견 목록(사람이 재현 검증할 대상)
    - 태스크별 발견 수 집계
  - 원본 보고가 아니라 **검증 노트 기준**으로 취합한다(기각된 발견 제외, 정정된
    심각도 반영).

### 2-3. 보류된 수정 (사람 결정 대기)

`docs/fix-plan-haiku.md`에 상세. 결정이 서면 활성화.

- [ ] **F1 (상, 서버 JWT)** — refresh 토큰이 access 토큰으로 사용 가능 + 로그아웃
  후에도 유효. **선결 확인**: 이 서버가 JWT 인증을 실제 사용 중인가
  (server/CLAUDE.md는 "userId=1 고정 MVP"라 하지만 코드는 JWT 인증 동작).
  실사용이면 수정(토큰 type 클레임 추가), 아니면 인증 도입 시점까지 유효하지 않음.
- [ ] **F9 (중, Android 위치)** — COARSE-only 허용 시 온보딩 통과하나 수집 불발.
  **정책 결정**: (A) 온보딩을 FINE 기준으로 안내/재요청 vs (B) 수집기가 COARSE 허용.
  위치 수집이 요구하는 좌표 정확도에 따라 팀이 결정.

### 2-4. 열린 PR 정리 (사람 담당)

리뷰·병합 대기: **#355, #356, #357, #358, #359, #360, #361** (모두 base develop).
각 PR은 독립적이며 파일 충돌 없음. 병합 순서 제약 없음.

---

## 3. 다음 세션 착수 지점(권장 순서)

1. 열린 PR 7건 리뷰·병합 (사람).
2. 미탐색 태스크 진행: **T2 → T4 → T6 → T7 → T9** (haiku 실행 + 검증).
   - T4는 PR #351 병합 후 최신 코드로.
3. 전부 끝나면 **T12 취합**으로 `SUMMARY.md` 생성.
4. 보류 결정(F1·F9)이 서면 해당 수정 활성화.
