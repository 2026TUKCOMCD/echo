package com.example.echo.memory.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingCodecTest {

    @Test
    @DisplayName("float 배열을 인코딩 후 디코딩하면 원래 값이 그대로 나온다")
    void roundTrip() {
        float[] vector = {0.1f, -0.25f, 1.0f, 0f, Float.MIN_VALUE, -3.5e-7f};

        float[] decoded = EmbeddingCodec.decode(EmbeddingCodec.encode(vector));

        assertThat(decoded).containsExactly(vector);
    }

    @Test
    @DisplayName("512차원은 2048바이트로 저장된다")
    void encodedSize() {
        assertThat(EmbeddingCodec.encode(new float[512])).hasSize(2048);
    }

    @Test
    @DisplayName("리틀엔디안으로 저장된다 - 1.0f(0x3F800000)의 첫 바이트가 최하위 바이트")
    void littleEndian() {
        byte[] bytes = EmbeddingCodec.encode(new float[]{1.0f});

        assertThat(bytes).containsExactly(0x00, 0x00, (byte) 0x80, 0x3F);
    }

    @Test
    @DisplayName("길이가 4의 배수가 아닌 바이트는 거부한다")
    void rejectsBrokenLength() {
        assertThatThrownBy(() -> EmbeddingCodec.decode(new byte[5]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
