package com.example.echo.conversation.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Mp3Silence - 조각 사이 무음 MP3 프레임")
class Mp3SilenceTest {

    /** MPEG-1 Layer III, 128kbps, 44.1kHz, mono, CRC 없음 - 프레임 417바이트(패딩 시 418) */
    static final byte[] MPEG1_HEADER = {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, (byte) 0xC4};
    /** MPEG-2 Layer III, 64kbps, 24kHz, mono - 프레임 192바이트 */
    static final byte[] MPEG2_HEADER = {(byte) 0xFF, (byte) 0xF3, (byte) 0x84, (byte) 0xC4};

    /** 헤더 + 0이 아닌 데이터로 채운 프레임 n개 (padded면 패딩 비트를 켜고 1바이트 더) */
    static byte[] frames(byte[] header, int frameBytes, int count, boolean padded) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < count; i++) {
            byte[] frame = new byte[frameBytes + (padded ? 1 : 0)];
            System.arraycopy(header, 0, frame, 0, 4);
            if (padded) {
                frame[2] |= 0x02;
            }
            for (int j = 4; j < frame.length; j++) {
                frame[j] = 0x55;
            }
            out.writeBytes(frame);
        }
        return out.toByteArray();
    }

    @Test
    @DisplayName("MPEG-1 프레임 헤더를 찾아 샘플레이트/프레임 크기를 읽는다")
    void findHeader_mpeg1() {
        byte[] data = frames(MPEG1_HEADER, 417, 3, false);

        Mp3Silence.FrameHeader header = Mp3Silence.findHeader(data, data.length);

        assertThat(header).isNotNull();
        assertThat(header.sampleRate()).isEqualTo(44100);
        assertThat(header.frameBytes()).isEqualTo(417);
        assertThat(header.samplesPerFrame()).isEqualTo(1152);
    }

    @Test
    @DisplayName("패딩된 프레임도 다음 헤더 위치를 맞게 계산한다")
    void findHeader_paddedFrames() {
        byte[] data = frames(MPEG1_HEADER, 417, 3, true);

        assertThat(Mp3Silence.findHeader(data, data.length)).isNotNull();
    }

    @Test
    @DisplayName("MPEG-2 저비트레이트 프레임도 읽는다")
    void findHeader_mpeg2() {
        byte[] data = frames(MPEG2_HEADER, 192, 2, false);

        Mp3Silence.FrameHeader header = Mp3Silence.findHeader(data, data.length);

        assertThat(header).isNotNull();
        assertThat(header.sampleRate()).isEqualTo(24000);
        assertThat(header.frameBytes()).isEqualTo(192);
        assertThat(header.samplesPerFrame()).isEqualTo(576);
    }

    @Test
    @DisplayName("앞의 ID3v2 태그는 건너뛴다")
    void findHeader_skipsId3v2() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tag = new byte[10 + 20];
        tag[0] = 'I';
        tag[1] = 'D';
        tag[2] = '3';
        tag[3] = 4;
        tag[9] = 20; // synchsafe 크기 20
        tag[12] = (byte) 0xFF; // 태그 안의 sync처럼 보이는 바이트
        tag[13] = (byte) 0xFB;
        out.writeBytes(tag);
        out.writeBytes(frames(MPEG1_HEADER, 417, 2, false));
        byte[] data = out.toByteArray();

        assertThat(Mp3Silence.findHeader(data, data.length)).isNotNull();
    }

    @Test
    @DisplayName("다음 프레임 헤더까지 확인되지 않으면 헤더로 인정하지 않는다")
    void findHeader_requiresNextFrame() {
        byte[] oneFrame = frames(MPEG1_HEADER, 417, 1, false);

        assertThat(Mp3Silence.findHeader(oneFrame, oneFrame.length)).isNull();
    }

    @Test
    @DisplayName("MP3가 아닌 데이터에서는 null")
    void findHeader_notMp3() {
        byte[] data = "mp3-1 이 아닌 가짜 오디오".getBytes();

        assertThat(Mp3Silence.findHeader(data, data.length)).isNull();
    }

    @Test
    @DisplayName("무음은 프레임 단위로 올림하고, 헤더는 패딩/CRC를 끄며 나머지는 0이다")
    void silence_framesRoundedUpAndZeroFilled() {
        byte[] source = frames(MPEG1_HEADER, 417, 2, true);
        source[1] &= (byte) ~0x01; // 원본에 CRC가 있어도
        Mp3Silence.FrameHeader header = Mp3Silence.findHeader(source, source.length);

        byte[] silence = Mp3Silence.silence(header, 350);

        // 44100 * 0.35 = 15435 샘플 → 1152 단위 올림 = 14프레임
        assertThat(silence).hasSize(14 * 417);
        for (int f = 0; f < 14; f++) {
            int off = f * 417;
            assertThat(silence[off]).isEqualTo((byte) 0xFF);
            assertThat(silence[off + 1]).isEqualTo((byte) 0xFB);
            assertThat(silence[off + 2]).isEqualTo((byte) 0x90);
            assertThat(silence[off + 3]).isEqualTo((byte) 0xC4);
            for (int i = off + 4; i < off + 417; i++) {
                assertThat(silence[i]).isZero();
            }
        }
        assertThat(Mp3Silence.findHeader(silence, silence.length)).isNotNull();
    }

    @Test
    @DisplayName("길이가 0 이하면 빈 배열")
    void silence_zeroDuration() {
        byte[] source = frames(MPEG1_HEADER, 417, 2, false);
        Mp3Silence.FrameHeader header = Mp3Silence.findHeader(source, source.length);

        assertThat(Mp3Silence.silence(header, 0)).isEmpty();
    }
}
