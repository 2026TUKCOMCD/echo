package com.example.echo.voice.realtime;

import java.io.ByteArrayOutputStream;

/**
 * 16-bit little-endian 모노 PCM 리샘플러 (선형 보간, 조각 단위 스트리밍).
 *
 * 앱은 16kHz로 녹음하고 OpenAI Realtime은 24kHz PCM을 받는다. 조각 경계에서 끊김이 없도록 직전 조각의 마지막
 * 샘플과 출력 위치를 이어서 기억한다. 조각이 홀수 바이트로 잘려 와도 남은 1바이트를 다음 조각 앞에 붙인다.
 * 한 발화에만 쓰고 버린다(스레드 안전하지 않음).
 */
public class Pcm16Resampler {

    private final int fromRate;
    private final int toRate;

    /** 지금까지 받은 입력 샘플 수 (현재 조각 이전까지) */
    private long inputCount;
    /** 입력 샘플 inputCount-1 번째 값 */
    private short lastSample;
    /** 다음에 만들 출력 샘플 번호 */
    private long outputIndex;
    /** 직전 조각에서 남은 홀수 바이트 (-1이면 없음) */
    private int pendingByte = -1;

    public Pcm16Resampler(int fromRate, int toRate) {
        if (fromRate <= 0 || toRate <= 0) {
            throw new IllegalArgumentException("sample rate must be positive");
        }
        this.fromRate = fromRate;
        this.toRate = toRate;
    }

    public byte[] process(byte[] chunk) {
        short[] samples = toSamples(chunk);
        if (samples.length == 0) {
            return new byte[0];
        }
        long lastIndex = inputCount + samples.length - 1;
        ByteArrayOutputStream out = new ByteArrayOutputStream(samples.length * toRate / fromRate * 2 + 4);
        while (true) {
            // 출력 샘플 n의 입력 위치 = n * fromRate / toRate (정수부 i, 소수부 remainder/toRate)
            long scaled = outputIndex * fromRate;
            long i = scaled / toRate;
            long remainder = scaled % toRate;
            boolean available = remainder == 0 ? i <= lastIndex : i + 1 <= lastIndex;
            if (!available) {
                break;
            }
            int a = sampleAt(i, samples);
            int value = remainder == 0 ? a : (int) (a + (sampleAt(i + 1, samples) - a) * remainder / toRate);
            out.write(value & 0xFF);
            out.write((value >> 8) & 0xFF);
            outputIndex++;
        }
        lastSample = samples[samples.length - 1];
        inputCount += samples.length;
        return out.toByteArray();
    }

    private int sampleAt(long index, short[] samples) {
        return index < inputCount ? lastSample : samples[(int) (index - inputCount)];
    }

    private short[] toSamples(byte[] chunk) {
        int offset = 0;
        int total = chunk.length + (pendingByte >= 0 ? 1 : 0);
        short[] samples = new short[total / 2];
        int s = 0;
        if (pendingByte >= 0 && chunk.length > 0) {
            samples[s++] = (short) ((chunk[0] << 8) | pendingByte);
            offset = 1;
            pendingByte = -1;
        }
        for (; offset + 1 < chunk.length; offset += 2) {
            samples[s++] = (short) ((chunk[offset + 1] << 8) | (chunk[offset] & 0xFF));
        }
        if (offset < chunk.length) {
            pendingByte = chunk[offset] & 0xFF;
        }
        return samples;
    }
}
