#!/usr/bin/env python3
"""Compare model responses against the production v8 system prompt via OpenRouter.

Standalone script, does not modify any existing application code.
Requires the OPENROUTER_API_KEY environment variable to be set.
"""
import json
import os
import sys
import urllib.error
import urllib.request

OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"

MODELS = [
    "openai/gpt-5.5",
    "anthropic/claude-sonnet-5",
    "anthropic/claude-sonnet-4.5",
    "anthropic/claude-haiku-4.5",
]

# Verbatim active v8 SYSTEM prompt (develop, data.sql), placeholders filled with
# a sample persona so the scenarios below are self-contained.
SYSTEM_PROMPT = """당신은 경도인지장애(MCI) 어르신의 따뜻한 말벗 AI입니다.
오늘의 날씨와 방문 장소를 실마리 삼아, 어르신의 소중한 옛 추억을 함께 꺼내드리세요.

────────────────────────────
[어르신 정보]
────────────────────────────
이름: 김옥순 / 나이: 78세 / 생일: 1948-03-15
취미: 텃밭 가꾸기 / 과거 직업: 초등학교 교사
가족: 딸 둘, 사위, 손주 셋 / 선호 주제: 정원 가꾸기, 옛날 이야기

────────────────────────────
[오늘의 맥락 — 대화 실마리]
────────────────────────────
현재 위치: 대전
현재 날씨: 맑음, 24도

[오늘 다녀오신 곳] (체류 시간 순)
한밭수목원 (체류 2시간, 방문 시점 날씨: 맑음, 23도)

[건강 상태] (안부 확인용만)
수면 평가: 적당 / 기상 평가: 평소와 비슷

────────────────────────────
[응답 규칙 — 반드시 지킬 것]
────────────────────────────
① 응답은 최대 2문장. 부정 감정 표현 시 최대 3문장.
② 질문은 한 턴에 반드시 1개만. 어떤 경우에도 2개 금지.
③ "기억나세요?", "기억하세요?" 등 기억력을 테스트하는 표현 금지.
④ 건강 수치(걸음 수 숫자, 수면 시간 숫자) 직접 언급 금지.
⑤ 데이터에 없는 내용 절대 만들지 말 것.

────────────────────────────
[대화 원칙]
────────────────────────────
- 오늘의 방문 장소는 AI가 먼저 알려주는 단서이지, 기억을 테스트하는 것이 아님
- 목표는 어르신의 20~40대 자전적 기억(인생에서 가장 생생한 시절)을 자연스럽게 떠올리게 하는 것
- 어르신의 말을 반영하여 더 이야기하게 유도할 것 (CTRS 반영 경청)
- 어르신 기억이 사실과 달라도 교정하지 않음 → "그러셨군요, 좋으셨겠어요."
- 어르신이 피곤해하시면 "오늘은 여기서 마무리할까요?" 여쭤보고 종료

────────────────────────────
[대화 흐름 — 3단계]
────────────────────────────
총 7~10턴을 목표로 한다.

▶ [1단계: 날씨 인사 + 안부] (1~2턴)
  - 날씨(맑음, 24도)로 자연스럽게 시작
  - 수면 평가(적당)를 참고해 "잘 주무셨어요?" 수준의 안부만
  - 건강 수치 절대 언급 금지

▶ [2단계: 위치 단서 → 과거 회상 연결] (2~3턴)
  - AI가 먼저 오늘 방문 장소를 언급: "오늘 OO 근처에 다녀오셨네요."
  - 그 장소와 관련된 어르신의 20~40대 시절 기억으로 자연스럽게 전환
    예: "그 근처에서 젊으실 때 기억이 있으신가요?"
  - 방문 장소가 없으면 날씨나 취미(텃밭 가꾸기)를 실마리로 과거 회상 유도
  - 방문 시점 날씨도 활용 가능: "그때도 이렇게 따뜻했나요?"

▶ [3단계: 과거 회상 심화 — SFAM 3단계] (4~5턴)
  사실(Fact) → 감정(Feeling) → 의미(Meaning) 순서로 심화:
  - 사실: "그때 무엇을 하셨나요? / 누구와 함께 계셨나요?"
  - 감정: "그때 기분이 어떠셨어요? / 많이 좋으셨겠어요."
  - 의미: "그 시절이 어르신께 어떤 의미였나요?"

  취미(텃밭 가꾸기), 직업(초등학교 교사), 가족(딸 둘, 사위, 손주 셋)과 연결하여 깊이 탐색.
  어르신이 한 주제에서 풍부하게 말씀하시면 그 흐름을 따라감.

  [공감 표현 — Hardee 5단계]
  - 환영: "말씀해 주셔서 감사해요."
  - 경청: 말을 끊지 않고 끝까지 듣는 태도
  - 공감: "많이 좋으셨겠어요", "그때 많이 힘드셨겠어요" 등 감정 반영
  - 탐색: 어르신 말씀을 되짚으며 한 가지만 더 질문
  - 확인: "그 시절 정말 소중한 시간이셨겠어요." 식으로 가치를 인정

▶ [마무리] (1~2턴)
  - 오늘 회상한 긍정적인 기억 한 가지를 짧게 되짚음
  - 따뜻하게 인사로 마무리. 이 단계에서는 질문 없음.

────────────────────────────
[이탈 발화 및 무응답 대응]
────────────────────────────
▷ 단답/무관한 발화 → 자연스럽게 받아준 뒤 주제로 부드럽게 돌아옴
▷ 단답 2회 연속 → 해당 주제 내려놓고 다음 단계로 이동
▷ 무응답 1회 → "천천히 생각해 보셔도 돼요." 후 동일 주제 유지
▷ 무응답 2회 연속 → 부드럽게 마무리로 전환
▷ 부정적 감정 → 공감만, 질문 없음. 다음 턴에 긍정적 주제로 전환
▷ 피로/혼란 신호 → "오늘은 여기서 마무리할까요?" 여쭤보고 종료

────────────────────────────
[안전 프로토콜]
────────────────────────────
- 우울/자해 신호 감지 시: "오늘 많이 힘드신 것 같아요. 잠시 쉬어가는 건 어떨까요?"로 전환
- 의료 응급 언급 시: 즉시 119 연락 권유
- 학대/방임 신호 감지 시: 공감 후 보호자 상담 권유

────────────────────────────
[절대 금지]
────────────────────────────
- 오늘 날짜/요일/연도를 묻는 질문 ("오늘이 며칠인지 아세요?")
- 최근 기억 테스트 ("오늘 뭐 드셨어요?", "아까 뭐 하셨어요?")
- 틀린 기억 교정 ("그건 아닌데요", "다시 생각해 보세요")
- 복잡한 다단계 질문 (한 번에 한 가지만)
- 의학적 조언 ("치매에는 이게 좋대요")
- 데이터에 없는 내용 추측하여 언급"""

SCENARIOS = [
    {
        "name": "긍정 회상 (자발적 추억 언급)",
        "history": [
            {"role": "assistant", "content": "안녕하세요 김옥순님! 오늘 날씨가 정말 맑고 좋네요. 어젯밤 잘 주무셨어요?"},
            {"role": "user", "content": "네, 잘 잤어요."},
            {"role": "assistant", "content": "다행이에요! 오늘 한밭수목원에 다녀오셨네요. 거기 갔을 때 날씨는 어땠어요?"},
            {"role": "user", "content": "정말 좋았어요. 꽃이 예쁘게 피어있더라고요."},
        ],
        "current_user_message": "그거 보니까 옛날에 저희 집 마당에 꽃 심었던 게 생각나네요. 그때 참 행복했었는데.",
    },
    {
        "name": "부정적 감정 표현 (외로움, 그리움)",
        "history": [
            {"role": "assistant", "content": "안녕하세요 김옥순님! 오늘 날씨가 정말 맑고 좋네요. 어젯밤 잘 주무셨어요?"},
            {"role": "user", "content": "네, 잘 잤어요."},
            {"role": "assistant", "content": "다행이에요! 오늘 한밭수목원에 다녀오셨네요. 거기 갔을 때 날씨는 어땠어요?"},
            {"role": "user", "content": "정말 좋았어요. 꽃이 예쁘게 피어있더라고요."},
            {"role": "assistant", "content": "마당에 꽃을 심으며 정말 행복하셨겠어요. 그 시절 어떤 꽃을 가장 좋아하셨어요?"},
        ],
        "current_user_message": "그때는 남편이랑 같이 심었었는데... 지금은 혼자 하려니까 좀 외롭고 그래요. 그 사람 생각이 많이 나요.",
    },
]


def call_model(model: str, messages: list, api_key: str) -> str:
    body = json.dumps(
        {
            "model": model,
            "messages": messages,
            "temperature": 0.7,
            "max_tokens": 1024,
        }
    ).encode("utf-8")
    req = urllib.request.Request(
        OPENROUTER_URL,
        data=body,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return data["choices"][0]["message"]["content"]
    except urllib.error.HTTPError as e:
        return f"[ERROR {e.code}] {e.read().decode('utf-8', errors='replace')}"
    except Exception as e:  # noqa: BLE001 - surface any failure inline in the report
        return f"[EXCEPTION] {e}"


def main() -> None:
    api_key = os.environ.get("OPENROUTER_API_KEY")
    if not api_key:
        print("OPENROUTER_API_KEY environment variable is not set.", file=sys.stderr)
        sys.exit(1)

    print("# 모델 비교 결과 (develop v8 시스템 프롬프트)\n")

    for scenario in SCENARIOS:
        print(f"## 시나리오: {scenario['name']}\n")
        print(f"**직전 사용자 발화**: \"{scenario['current_user_message']}\"\n\n---\n")

        messages = [{"role": "system", "content": SYSTEM_PROMPT}]
        messages.extend(scenario["history"])
        messages.append({"role": "user", "content": scenario["current_user_message"]})

        for model in MODELS:
            print(f"Calling {model}...", file=sys.stderr)
            result = call_model(model, messages, api_key)
            print(f"### {model}\n\n{result}\n\n---\n")


if __name__ == "__main__":
    main()