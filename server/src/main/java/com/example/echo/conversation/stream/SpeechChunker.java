package com.example.echo.conversation.stream;

/**
 * LLM이 스트리밍으로 내보내는 텍스트를 "첫 조각 + 나머지" 두 덩어리로 나눈다.
 *
 * 첫 조각이 정해지는 즉시 TTS를 시작해 첫 소리를 앞당기고, 나머지는 LLM이 끝난 뒤 한 번에 합성한다.
 * eleven_v3는 조각을 자연스럽게 이어 주는 request stitching을 지원하지 않아 이음새를 1곳으로 제한한다.
 *
 * <ul>
 *   <li>문장 끝: {@code . ? ! … ~} 와 줄바꿈. 뒤따르는 문장부호/닫는 따옴표까지 포함하고, 그 뒤에 공백이 와야
 *       끝으로 본다 - "1.5km"의 소수점처럼 공백 없이 이어지는 점은 문장 끝이 아니다.</li>
 *   <li>첫 조각은 최소 {@code minChars}자: 첫 문장이 짧으면("그러셨군요!") 다음 문장까지 붙인다.
 *       너무 짧은 첫 조각은 재생이 금방 끝나 나머지 합성을 기다리는 공백이 생기기 때문이다.</li>
 *   <li>문장 끝 없이 {@code maxChars}자를 넘으면 쉼표나 공백에서 자른다(첫 소리가 무한정 늦어지지 않도록).</li>
 * </ul>
 *
 * 스레드 안전하지 않다 - LLM 스트림을 읽는 한 스레드에서만 쓴다.
 */
public final class SpeechChunker {

    private final int minChars;
    private final int maxChars;
    private final StringBuilder buffer = new StringBuilder();
    private boolean firstEmitted;

    public SpeechChunker(int minChars, int maxChars) {
        if (minChars < 1 || maxChars < minChars) {
            throw new IllegalArgumentException("minChars=" + minChars + ", maxChars=" + maxChars);
        }
        this.minChars = minChars;
        this.maxChars = maxChars;
    }

    /**
     * LLM 텍스트 조각을 추가한다.
     *
     * @return 이번 추가로 첫 조각이 확정되면 그 텍스트(앞뒤 공백 제거), 아니면 null. 첫 조각은 한 번만 반환된다.
     */
    public String append(String delta) {
        if (delta == null || delta.isEmpty()) {
            return null;
        }
        buffer.append(delta);
        if (firstEmitted) {
            return null;
        }

        int cut = findSentenceCut();
        if (cut < 0 && leadingTrimmedLength(buffer.length()) > maxChars) {
            cut = findForcedCut();
        }
        if (cut < 0) {
            return null;
        }

        String first = buffer.substring(0, cut).trim();
        buffer.delete(0, cut);
        firstEmitted = true;
        return first;
    }

    /**
     * LLM 스트림이 끝났을 때 호출한다.
     *
     * @return 아직 내보내지 않은 나머지 텍스트(앞뒤 공백 제거, 없으면 빈 문자열).
     *         첫 조각이 확정되기 전에 끝났다면 전체가 하나의 조각으로 반환된다.
     */
    public String finish() {
        String rest = buffer.toString().trim();
        buffer.setLength(0);
        firstEmitted = true;
        return rest;
    }

    public boolean isFirstEmitted() {
        return firstEmitted;
    }

    /** 최소 길이를 넘긴 첫 문장 끝의 위치(자를 인덱스, 뒤따르는 공백 직전). 없으면 -1. */
    private int findSentenceCut() {
        int i = 0;
        while (i < buffer.length()) {
            char c = buffer.charAt(i);
            if (c == '\n') {
                if (leadingTrimmedLength(i) >= minChars) {
                    return i;
                }
                i++;
                continue;
            }
            if (!isTerminator(c)) {
                i++;
                continue;
            }

            // 연속된 문장부호("?!", "...", "~!")와 닫는 따옴표/괄호까지 한 문장 끝으로 묶는다
            int end = i + 1;
            while (end < buffer.length() && (isTerminator(buffer.charAt(end)) || isCloser(buffer.charAt(end)))) {
                end++;
            }
            // 버퍼 끝이면 뒤에 무엇이 올지 모르므로(소수점일 수 있음) 다음 조각을 기다린다
            if (end >= buffer.length()) {
                return -1;
            }
            if (Character.isWhitespace(buffer.charAt(end)) && leadingTrimmedLength(end) >= minChars) {
                return end;
            }
            i = end;
        }
        return -1;
    }

    /** maxChars 안쪽의 마지막 쉼표(뒤) 또는 공백에서 자른다. 둘 다 없으면 maxChars에서 자른다. */
    private int findForcedCut() {
        int start = leadingWhitespace();
        int limit = Math.min(buffer.length(), start + maxChars);
        for (int i = limit - 1; i > start + minChars; i--) {
            if (buffer.charAt(i) == ',') {
                return i + 1;
            }
        }
        for (int i = limit - 1; i > start + minChars; i--) {
            if (Character.isWhitespace(buffer.charAt(i))) {
                return i;
            }
        }
        return limit;
    }

    private int leadingTrimmedLength(int end) {
        return buffer.substring(0, end).trim().length();
    }

    private int leadingWhitespace() {
        int i = 0;
        while (i < buffer.length() && Character.isWhitespace(buffer.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isTerminator(char c) {
        return c == '.' || c == '?' || c == '!' || c == '…' || c == '~';
    }

    private static boolean isCloser(char c) {
        return c == '"' || c == '\'' || c == ')' || c == '”' || c == '’' || c == '」' || c == '』';
    }
}
