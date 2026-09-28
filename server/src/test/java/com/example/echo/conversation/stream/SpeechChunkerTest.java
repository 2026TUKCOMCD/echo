package com.example.echo.conversation.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SpeechChunker - 첫 조각 + 나머지 분할")
class SpeechChunkerTest {

    /** LLM처럼 몇 글자씩 나눠 넣고, 첫 조각이 나온 시점까지 넣은 글자 수와 결과를 돌려준다 */
    private static Result feed(SpeechChunker chunker, String text, int step) {
        String first = null;
        int fedWhenFirst = -1;
        for (int i = 0; i < text.length(); i += step) {
            String emitted = chunker.append(text.substring(i, Math.min(text.length(), i + step)));
            if (emitted != null) {
                assertThat(first).as("첫 조각은 한 번만 나와야 함").isNull();
                first = emitted;
                fedWhenFirst = Math.min(text.length(), i + step);
            }
        }
        return new Result(first, fedWhenFirst, chunker.finish());
    }

    private record Result(String first, int fedWhenFirst, String rest) {
    }

    @Test
    @DisplayName("최소 길이를 넘는 첫 문장에서 자르고, 나머지는 finish()로 받는다")
    void cutsAtFirstSentenceOverMinimum() {
        Result r = feed(new SpeechChunker(15, 80), "오늘 산책은 어디로 다녀오셨어요? 날씨가 참 좋았지요.", 3);

        assertThat(r.first()).isEqualTo("오늘 산책은 어디로 다녀오셨어요?");
        assertThat(r.rest()).isEqualTo("날씨가 참 좋았지요.");
    }

    @Test
    @DisplayName("첫 문장이 최소 길이보다 짧으면 다음 문장까지 붙인다")
    void shortFirstSentence_mergedWithNext() {
        Result r = feed(new SpeechChunker(15, 80), "그러셨군요! 오늘 산책은 어디로 다녀오셨어요? 궁금해요.", 2);

        assertThat(r.first()).isEqualTo("그러셨군요! 오늘 산책은 어디로 다녀오셨어요?");
        assertThat(r.rest()).isEqualTo("궁금해요.");
    }

    @Test
    @DisplayName("문장 끝 뒤에 공백이 와야 확정한다 - 소수점(1.5km)은 문장 끝이 아니다")
    void decimalPointIsNotSentenceEnd() {
        Result r = feed(new SpeechChunker(5, 80), "오늘 1.5km나 걸으셨네요. 대단하세요!", 1);

        assertThat(r.first()).isEqualTo("오늘 1.5km나 걸으셨네요.");
        assertThat(r.rest()).isEqualTo("대단하세요!");
    }

    @Test
    @DisplayName("문장부호가 버퍼 끝에 있으면 다음 글자를 볼 때까지 기다린다")
    void terminatorAtBufferEnd_waitsForNextChar() {
        SpeechChunker chunker = new SpeechChunker(5, 80);

        assertThat(chunker.append("오늘 산책은 좋으셨어요?")).isNull();
        assertThat(chunker.append(" 네")).isEqualTo("오늘 산책은 좋으셨어요?");
    }

    @Test
    @DisplayName("연속된 문장부호와 닫는 따옴표까지 한 문장 끝으로 묶는다")
    void groupsTrailingPunctuationAndClosers() {
        Result r = feed(new SpeechChunker(5, 80), "정말 \"대단하세요!\"… 또 들려주세요.", 1);

        assertThat(r.first()).isEqualTo("정말 \"대단하세요!\"…");
        assertThat(r.rest()).isEqualTo("또 들려주세요.");
    }

    @Test
    @DisplayName("물결(~)과 줄바꿈도 문장 끝으로 본다")
    void tildeAndNewlineAreSentenceEnds() {
        assertThat(feed(new SpeechChunker(5, 80), "오늘도 반가워요~ 잘 지내셨어요?", 1).first())
                .isEqualTo("오늘도 반가워요~");
        assertThat(feed(new SpeechChunker(5, 80), "오늘도 반가워요\n잘 지내셨어요?", 1).first())
                .isEqualTo("오늘도 반가워요");
    }

    @Test
    @DisplayName("문장 끝 없이 최대 길이를 넘으면 마지막 쉼표 뒤에서 자른다")
    void noSentenceEnd_overMax_cutsAfterLastComma() {
        String text = "어르신 오늘은 아침부터 공원에 다녀오시고, 점심에는 따님과 식사를 하시고, 오후에는 복지관에서 친구분들과 이야기를 나누셨다고 들었는데 어떠셨어요";
        Result r = feed(new SpeechChunker(15, 40), text, 1);

        assertThat(r.first()).isEqualTo("어르신 오늘은 아침부터 공원에 다녀오시고,");
        assertThat(r.first().length()).isLessThanOrEqualTo(40);
        assertThat(r.first() + " " + r.rest()).isEqualTo(text);
    }

    @Test
    @DisplayName("쉼표도 없으면 마지막 공백에서 자른다")
    void noComma_cutsAtLastSpace() {
        String text = "어르신 오늘은 아침부터 공원에 다녀오시고 점심에는 따님과 식사를 하셨다고 들었어요";
        Result r = feed(new SpeechChunker(10, 30), text, 1);

        assertThat(r.first().length()).isLessThanOrEqualTo(30);
        assertThat(text).startsWith(r.first());
        assertThat(r.first() + " " + r.rest()).isEqualTo(text);
    }

    @Test
    @DisplayName("첫 조각 기준을 못 채우고 끝나면 finish()가 전체를 하나의 조각으로 준다")
    void shortWholeResponse_returnedByFinish() {
        Result r = feed(new SpeechChunker(15, 80), "네, 좋아요!", 2);

        assertThat(r.first()).isNull();
        assertThat(r.rest()).isEqualTo("네, 좋아요!");
    }

    @Test
    @DisplayName("첫 조각 뒤에 남은 글이 없으면 finish()는 빈 문자열")
    void nothingAfterFirst_finishReturnsEmpty() {
        SpeechChunker chunker = new SpeechChunker(5, 80);

        assertThat(chunker.append("오늘도 반가워요. ")).isEqualTo("오늘도 반가워요.");
        assertThat(chunker.finish()).isEmpty();
    }

    @Test
    @DisplayName("조각 크기와 상관없이 같은 결과가 나온다 (한 번에 넣기 / 한 글자씩)")
    void resultIndependentOfDeltaSize() {
        String text = "그러셨군요! 오늘 산책은 어디로 다녀오셨어요? 궁금해요.";
        List<Result> results = new ArrayList<>();
        for (int step : new int[]{1, 2, 5, text.length()}) {
            Result r = feed(new SpeechChunker(15, 80), text, step);
            results.add(new Result(r.first(), 0, r.rest()));
        }
        assertThat(results).allMatch(r -> r.equals(results.get(0)));
    }

    @Test
    @DisplayName("잘못된 설정값은 거부한다")
    void rejectsInvalidConfig() {
        assertThatThrownBy(() -> new SpeechChunker(0, 80)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpeechChunker(20, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
