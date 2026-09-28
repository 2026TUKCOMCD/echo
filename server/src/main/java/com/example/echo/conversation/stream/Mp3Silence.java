package com.example.echo.conversation.stream;

/**
 * TTS 조각 사이에 끼워 넣을 무음 MP3 프레임을 만든다.
 *
 * 조각을 따로 합성하면 ElevenLabs가 앞뒤 무음을 거의 남기지 않아, 앞 조각의 마침표 뒤에 쉬는 틈 없이
 * 다음 조각이 바로 이어진다. 그래서 이음새에 짧은 무음을 넣는다.
 *
 * 무음 프레임은 실제 스트림의 프레임 헤더(샘플레이트/비트레이트/채널)를 그대로 복사하고 나머지를 0으로 채운다.
 * side info가 전부 0이면(part2_3_length=0) 디코더는 이 프레임을 무음으로 재생한다.
 * 출력 형식을 가정하지 않고 받은 헤더를 따르므로, TTS 출력 형식이 바뀌어도 그대로 맞춰진다.
 *
 * MPEG-1/2/2.5 Layer III만 다룬다. 헤더를 찾지 못하면 호출자는 무음을 넣지 않는다(지금처럼 붙여서 재생).
 */
public final class Mp3Silence {

    private static final int[] BITRATES_V1_L3 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320};
    private static final int[] BITRATES_V2_L3 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160};
    private static final int[][] SAMPLE_RATES = {
            {11025, 12000, 8000},   // MPEG 2.5
            null,                   // reserved
            {22050, 24000, 16000},  // MPEG 2
            {44100, 48000, 32000}   // MPEG 1
    };

    private static final int ID3V2_HEADER_BYTES = 10;

    private Mp3Silence() {
    }

    /**
     * MP3 Layer III 프레임 헤더 (4바이트).
     *
     * @param frameBytes 패딩 없는 프레임 1개의 바이트 수
     * @param sampleRate 샘플레이트(Hz)
     * @param samplesPerFrame 프레임당 샘플 수 (MPEG-1: 1152, MPEG-2/2.5: 576)
     */
    public record FrameHeader(byte[] bytes, int frameBytes, int sampleRate, int samplesPerFrame) {
    }

    /**
     * 오디오 앞부분에서 첫 MP3 프레임 헤더를 찾는다. 앞에 ID3v2 태그가 있으면 건너뛴다.
     * 우연히 sync 비트처럼 보이는 바이트를 헤더로 잘못 읽지 않도록, 바로 다음 프레임 헤더까지 확인되는 경우만 인정한다.
     *
     * @return 찾은 헤더, 확인할 만큼 데이터가 없거나 MP3가 아니면 null
     */
    public static FrameHeader findHeader(byte[] data, int length) {
        int start = skipId3v2(data, length);
        for (int i = start; i + 4 <= length; i++) {
            FrameHeader header = parse(data, i);
            if (header == null) {
                continue;
            }
            int next = i + frameLength(data, i, header);
            if (next + 4 <= length && parse(data, next) != null) {
                return header;
            }
        }
        return null;
    }

    /**
     * 약 {@code durationMs} 길이의 무음 프레임들을 만든다(프레임 단위로 올림).
     * 헤더는 패딩 없음, CRC 없음으로 바꿔 쓴다 - CRC가 있으면 0으로 채운 데이터와 맞지 않기 때문이다.
     */
    public static byte[] silence(FrameHeader header, int durationMs) {
        if (durationMs <= 0) {
            return new byte[0];
        }
        long samples = (long) header.sampleRate() * durationMs / 1000;
        int frames = (int) ((samples + header.samplesPerFrame() - 1) / header.samplesPerFrame());

        byte[] frame = new byte[header.frameBytes()];
        byte[] h = header.bytes();
        frame[0] = h[0];
        frame[1] = (byte) (h[1] | 0x01);   // protection bit = 1 (CRC 없음)
        frame[2] = (byte) (h[2] & ~0x02);  // padding bit = 0
        frame[3] = h[3];

        byte[] result = new byte[frame.length * frames];
        for (int i = 0; i < frames; i++) {
            System.arraycopy(frame, 0, result, i * frame.length, frame.length);
        }
        return result;
    }

    /** offset 위치를 Layer III 프레임 헤더로 해석한다. 유효하지 않으면 null. */
    static FrameHeader parse(byte[] data, int offset) {
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        int b2 = data[offset + 2] & 0xFF;
        int b3 = data[offset + 3] & 0xFF;

        if (b0 != 0xFF || (b1 & 0xE0) != 0xE0) {
            return null;
        }
        int version = (b1 >> 3) & 0x03;   // 0: 2.5, 1: reserved, 2: MPEG-2, 3: MPEG-1
        int layer = (b1 >> 1) & 0x03;     // 1: Layer III
        int bitrateIndex = (b2 >> 4) & 0x0F;
        int sampleRateIndex = (b2 >> 2) & 0x03;
        int emphasis = b3 & 0x03;
        if (version == 1 || layer != 1 || bitrateIndex == 0 || bitrateIndex == 15
                || sampleRateIndex == 3 || emphasis == 2) {
            return null;
        }

        boolean mpeg1 = version == 3;
        int bitrate = (mpeg1 ? BITRATES_V1_L3 : BITRATES_V2_L3)[bitrateIndex] * 1000;
        int sampleRate = SAMPLE_RATES[version][sampleRateIndex];
        int samplesPerFrame = mpeg1 ? 1152 : 576;
        int frameBytes = (samplesPerFrame / 8) * bitrate / sampleRate;

        byte[] bytes = {(byte) b0, (byte) b1, (byte) b2, (byte) b3};
        return new FrameHeader(bytes, frameBytes, sampleRate, samplesPerFrame);
    }

    private static int frameLength(byte[] data, int offset, FrameHeader header) {
        boolean padded = (data[offset + 2] & 0x02) != 0;
        return header.frameBytes() + (padded ? 1 : 0);
    }

    /** "ID3" 태그가 있으면 그 뒤 위치, 없으면 0. 크기는 synchsafe 정수(바이트당 7비트) */
    private static int skipId3v2(byte[] data, int length) {
        if (length < ID3V2_HEADER_BYTES || data[0] != 'I' || data[1] != 'D' || data[2] != '3') {
            return 0;
        }
        int size = ((data[6] & 0x7F) << 21) | ((data[7] & 0x7F) << 14) | ((data[8] & 0x7F) << 7) | (data[9] & 0x7F);
        boolean hasFooter = (data[5] & 0x10) != 0;
        return ID3V2_HEADER_BYTES + size + (hasFooter ? ID3V2_HEADER_BYTES : 0);
    }
}
