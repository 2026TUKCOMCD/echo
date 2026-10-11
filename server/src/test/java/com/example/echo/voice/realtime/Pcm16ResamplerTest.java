package com.example.echo.voice.realtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class Pcm16ResamplerTest {

    private static byte[] pcm(short... samples) {
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            bytes[i * 2] = (byte) samples[i];
            bytes[i * 2 + 1] = (byte) (samples[i] >> 8);
        }
        return bytes;
    }

    private static short[] samples(byte[] bytes) {
        short[] result = new short[bytes.length / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (short) ((bytes[i * 2 + 1] << 8) | (bytes[i * 2] & 0xFF));
        }
        return result;
    }

    private static short[] ramp(int length) {
        short[] values = new short[length];
        for (int i = 0; i < length; i++) {
            values[i] = (short) (i * 30 - 3000);
        }
        return values;
    }

    @Test
    @DisplayName("16kHz → 24kHz: 샘플 수가 1.5배가 되고, 입력 샘플 사이를 선형 보간한다")
    void upsamplesWithLinearInterpolation() {
        Pcm16Resampler resampler = new Pcm16Resampler(16_000, 24_000);

        short[] out = samples(resampler.process(pcm((short) 0, (short) 300, (short) 600, (short) 900)));

        // 입력 위치 0, 2/3, 4/3, 2, 8/3 (마지막 샘플 뒤는 다음 조각이 와야 계산)
        assertThat(out).containsExactly((short) 0, (short) 200, (short) 400, (short) 600, (short) 800);
    }

    @Test
    @DisplayName("조각으로 나눠 넣어도(홀수 바이트 경계 포함) 한 번에 넣은 결과와 같다")
    void chunkedEqualsWhole() {
        byte[] whole = pcm(ramp(1000));
        byte[] expected = new Pcm16Resampler(16_000, 24_000).process(whole);

        Pcm16Resampler chunked = new Pcm16Resampler(16_000, 24_000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int[] sizes = {1, 7, 64, 333, 1024, 2};
        int offset = 0;
        int index = 0;
        while (offset < whole.length) {
            int size = Math.min(sizes[index++ % sizes.length], whole.length - offset);
            byte[] chunk = new byte[size];
            System.arraycopy(whole, offset, chunk, 0, size);
            out.writeBytes(chunked.process(chunk));
            offset += size;
        }

        assertThat(out.toByteArray()).isEqualTo(expected);
    }

    @Test
    @DisplayName("빈 조각은 빈 결과")
    void emptyChunk() {
        assertThat(new Pcm16Resampler(16_000, 24_000).process(new byte[0])).isEmpty();
    }
}
