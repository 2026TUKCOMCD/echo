package com.example.echo.voice.provider;

import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.client.TTSClient;
import com.example.echo.voice.exception.VoiceProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AzureTtsProviderTest {

    @Mock
    private TTSClient ttsClient;

    private AzureTtsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AzureTtsProvider(ttsClient);
        ReflectionTestUtils.setField(provider, "defaultVoice", "ko-KR-SunHiNeural");
    }

    @Test
    @DisplayName("getName()은 azure를 반환")
    void getName_returnsAzure() {
        assertThat(provider.getName()).isEqualTo("azure");
    }

    @Test
    @DisplayName("정상 플로우: 텍스트 + VoiceSettings → 음성 바이트 배열 반환")
    void success() {
        VoiceSettings settings = VoiceSettings.builder().voiceSpeed(1.0).voiceTone("warm").build();
        byte[] expectedAudio = "fake-mp3-data".getBytes();

        when(ttsClient.synthesize(any())).thenReturn(expectedAudio);

        byte[] result = provider.synthesize("안녕하세요", settings);

        assertThat(result).isEqualTo(expectedAudio);
        verify(ttsClient, times(1)).synthesize(any());
    }

    @Test
    @DisplayName("VoiceSettings가 null이면 기본값(ko-KR-SunHiNeural, rate=+0%) 사용")
    void nullVoiceSettings_usesDefaults() {
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("테스트", null);

        verify(ttsClient).synthesize(contains("ko-KR-SunHiNeural"));
        verify(ttsClient).synthesize(contains("rate='+0%'"));
    }

    @Test
    @DisplayName("voiceTone=warm → voice=ko-KR-SunHiNeural")
    void warmTone_resolvesToSunHi() {
        VoiceSettings settings = VoiceSettings.builder().voiceTone("warm").build();
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("테스트", settings);

        verify(ttsClient).synthesize(contains("ko-KR-SunHiNeural"));
    }

    @Test
    @DisplayName("voiceTone=calm → voice=ko-KR-InJoonNeural")
    void calmTone_resolvesToInJoon() {
        VoiceSettings settings = VoiceSettings.builder().voiceTone("calm").build();
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("테스트", settings);

        verify(ttsClient).synthesize(contains("ko-KR-InJoonNeural"));
    }

    @Test
    @DisplayName("voiceTone=bright → voice=ko-KR-JiMinNeural")
    void brightTone_resolvesToJiMin() {
        VoiceSettings settings = VoiceSettings.builder().voiceTone("bright").build();
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("테스트", settings);

        verify(ttsClient).synthesize(contains("ko-KR-JiMinNeural"));
    }

    @Test
    @DisplayName("voiceTone=gentle → voice=ko-KR-YuJinNeural")
    void gentleTone_resolvesToYuJin() {
        VoiceSettings settings = VoiceSettings.builder().voiceTone("gentle").build();
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("테스트", settings);

        verify(ttsClient).synthesize(contains("ko-KR-YuJinNeural"));
    }

    @Test
    @DisplayName("알 수 없는 voiceTone → 기본 voice(ko-KR-SunHiNeural) 사용")
    void unknownTone_usesDefaultVoice() {
        VoiceSettings settings = VoiceSettings.builder().voiceTone("unknown").build();
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("테스트", settings);

        verify(ttsClient).synthesize(contains("ko-KR-SunHiNeural"));
    }

    @Test
    @DisplayName("voiceTone이 null이면 기본 voice 사용")
    void nullTone_usesDefaultVoice() {
        VoiceSettings settings = VoiceSettings.builder().voiceTone(null).build();
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("테스트", settings);

        verify(ttsClient).synthesize(contains("ko-KR-SunHiNeural"));
    }

    @Test
    @DisplayName("voiceSpeed=0.5 → rate='-50%'")
    void speed05_convertsToMinus50Percent() {
        VoiceSettings settings = VoiceSettings.builder().voiceSpeed(0.5).build();
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("테스트", settings);

        verify(ttsClient).synthesize(contains("rate='-50%'"));
    }

    @Test
    @DisplayName("voiceSpeed=1.0 → rate='+0%'")
    void speed10_convertsToPlus0Percent() {
        VoiceSettings settings = VoiceSettings.builder().voiceSpeed(1.0).build();
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("테스트", settings);

        verify(ttsClient).synthesize(contains("rate='+0%'"));
    }

    @Test
    @DisplayName("voiceSpeed=1.5 → rate='+50%'")
    void speed15_convertsToPlus50Percent() {
        VoiceSettings settings = VoiceSettings.builder().voiceSpeed(1.5).build();
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("테스트", settings);

        verify(ttsClient).synthesize(contains("rate='+50%'"));
    }

    @Test
    @DisplayName("voiceSpeed=2.0 → rate='+100%' (최댓값 제한)")
    void speed20_clampedToPlus100Percent() {
        VoiceSettings settings = VoiceSettings.builder().voiceSpeed(2.0).build();
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("테스트", settings);

        verify(ttsClient).synthesize(contains("rate='+100%'"));
    }

    @Test
    @DisplayName("voiceSpeed가 null이면 rate='+0%' 사용")
    void nullSpeed_convertsToPlus0Percent() {
        VoiceSettings settings = VoiceSettings.builder().voiceSpeed(null).build();
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("테스트", settings);

        verify(ttsClient).synthesize(contains("rate='+0%'"));
    }

    @Test
    @DisplayName("Azure TTS API 응답이 null이면 VoiceProcessingException 발생")
    void nullApiResponse_throwsException() {
        when(ttsClient.synthesize(any())).thenReturn(null);

        assertThatThrownBy(() -> provider.synthesize("테스트", null))
                .isInstanceOf(VoiceProcessingException.class)
                .hasMessage("Azure TTS API 응답이 비어있습니다.");
    }

    @Test
    @DisplayName("Azure TTS API 응답이 빈 배열이면 VoiceProcessingException 발생")
    void emptyApiResponse_throwsException() {
        when(ttsClient.synthesize(any())).thenReturn(new byte[0]);

        assertThatThrownBy(() -> provider.synthesize("테스트", null))
                .isInstanceOf(VoiceProcessingException.class)
                .hasMessage("Azure TTS API 응답이 비어있습니다.");
    }

    @Test
    @DisplayName("TTS 클라이언트에서 예외 발생 시 그대로 전파 (래핑은 VoiceServiceImpl 책임)")
    void clientException_propagates() {
        when(ttsClient.synthesize(any()))
                .thenThrow(new RuntimeException("Azure API 연결 실패"));

        assertThatThrownBy(() -> provider.synthesize("테스트", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Azure API 연결 실패");
    }

    @Test
    @DisplayName("SSML에 텍스트가 포함되어 전달됨")
    void ssml_containsText() {
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("안녕", null);

        verify(ttsClient).synthesize(contains("안녕"));
    }

    @Test
    @DisplayName("SSML에 speak 태그와 voice 태그가 포함됨")
    void ssml_containsSpeakAndVoiceTag() {
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("테스트", null);

        verify(ttsClient).synthesize(argThat(ssml ->
            ssml.contains("<speak") && ssml.contains("<voice") && ssml.contains("<prosody")
        ));
    }

    @Test
    @DisplayName("텍스트에 XML 특수문자(&)가 있으면 &amp;로 이스케이프")
    void xmlSpecialChar_ampersand_escaped() {
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("A&B", null);

        verify(ttsClient).synthesize(contains("A&amp;B"));
    }

    @Test
    @DisplayName("텍스트에 XML 특수문자(<)가 있으면 &lt;로 이스케이프")
    void xmlSpecialChar_lessThan_escaped() {
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("A<B", null);

        verify(ttsClient).synthesize(contains("A&lt;B"));
    }

    @Test
    @DisplayName("텍스트에 XML 특수문자(>)가 있으면 &gt;로 이스케이프")
    void xmlSpecialChar_greaterThan_escaped() {
        when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

        provider.synthesize("A>B", null);

        verify(ttsClient).synthesize(contains("A&gt;B"));
    }
}
