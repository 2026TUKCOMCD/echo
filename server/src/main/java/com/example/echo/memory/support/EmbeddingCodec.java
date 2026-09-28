package com.example.echo.memory.support;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 임베딩 벡터 ↔ DB BLOB 변환
 *
 * float32 하나를 4바이트로, 리틀엔디안 고정 (512차원 = 2KB)
 * - 바이트 순서를 고정해 두지 않으면 저장된 벡터를 다른 환경에서 읽을 때 값이 깨진다
 */
public final class EmbeddingCodec {

    private EmbeddingCodec() {
    }

    public static byte[] encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(vector);
        return buffer.array();
    }

    public static float[] decode(byte[] bytes) {
        if (bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException("임베딩 바이트 길이가 4의 배수가 아님: " + bytes.length);
        }
        float[] vector = new float[bytes.length / Float.BYTES];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(vector);
        return vector;
    }
}
