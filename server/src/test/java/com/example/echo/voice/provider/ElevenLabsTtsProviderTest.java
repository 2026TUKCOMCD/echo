package com.example.echo.voice.provider;

import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.client.ElevenLabsTtsClient;
import com.example.echo.voice.exception.RetryableVoiceException;
import com.example.echo.voice.exception.VoiceProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElevenLabsTtsProviderTest {

    @Mock
    private ElevenLabsTtsClient elevenLabsClient;

    private ElevenLabsTtsProvider provider;

    @BeforeEach
    void setUp() {
        // 테스트용 RetryTemplate: backoff 없이(0ms) 빠르게 실행, 재시도 정책은 ElevenLabsRetryConfig와 동일
        RetryTemplate testRetryTemplate = new RetryTemplate();
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = Map.of(
                RetryableVoiceException.class, true,
                VoiceProcessingException.class, false
        );
        testRetryTemplate.setRetryPolicy(new SimpleRetryPolicy(3, retryableExceptions, true));

        provider = new ElevenLabsTtsProvider(elevenLabsClient, testRetryTemplate);
        ReflectionTestUtils.setField(provider, "voiceId", "sf8Bpb1IU97NI9BHSMRf");
        ReflectionTestUtils.setField(provider, "model", "eleven_multilingual_v2");
    }

    @Test
    @DisplayName("getName()은 elevenlabs를 반환")
    void getName_returnsElevenlabs() {
        assertThat(provider.getName()).isEqualTo("elevenlabs");
    }

    @Test
    @DisplayName("정상 응답 → 음성 바이트 배열 반환")
    void success() {
        byte[] expectedAudio = "mp3-data".getBytes();
        when(elevenLabsClient.synthesize(any(), any())).thenReturn(expectedAudio);

        byte[] result = provider.synthesize("테스트", VoiceSettings.builder().voiceTone("warm").build());

        assertThat(result).isEqualTo(expectedAudio);
        verify(elevenLabsClient, times(1)).synthesize(eq("sf8Bpb1IU97NI9BHSMRf"), any());
    }

    @Test
    @DisplayName("응답이 비어있으면 VoiceProcessingException 발생 + 쿼터 로그 조회")
    void emptyResponse_throwsException_andLogsQuota() {
        when(elevenLabsClient.synthesize(any(), any())).thenReturn(new byte[0]);

        assertThatThrownBy(() -> provider.synthesize("테스트", null))
                .isInstanceOf(VoiceProcessingException.class)
                .hasMessage("ElevenLabs TTS API 응답이 비어있습니다.");

        verify(elevenLabsClient, times(1)).getSubscriptionInfo();
    }

    @Test
    @DisplayName("5xx 오류 → 3회 재시도 후 VoiceProcessingException 발생 + 쿼터 로그 조회")
    void retryableError_exhausted_throwsVoiceProcessingException() {
        when(elevenLabsClient.synthesize(any(), any()))
                .thenThrow(new RetryableVoiceException("서버 오류 (HTTP 500)"));

        assertThatThrownBy(() -> provider.synthesize("테스트", null))
                .isInstanceOf(VoiceProcessingException.class)
                .hasMessageContaining("일시적으로 불안정");

        verify(elevenLabsClient, times(3)).synthesize(any(), any());
        verify(elevenLabsClient, times(1)).getSubscriptionInfo();
    }

    @Test
    @DisplayName("1회 실패 후 성공 → 정상 반환 (재시도 동작 확인)")
    void retrySuccess_afterOneFailure() {
        byte[] expectedAudio = "mp3-data".getBytes();
        when(elevenLabsClient.synthesize(any(), any()))
                .thenThrow(new RetryableVoiceException("임시 오류"))
                .thenReturn(expectedAudio);

        byte[] result = provider.synthesize("테스트", null);

        assertThat(result).isEqualTo(expectedAudio);
        verify(elevenLabsClient, times(2)).synthesize(any(), any());
        verify(elevenLabsClient, never()).getSubscriptionInfo();
    }

    @Test
    @DisplayName("4xx(인증 실패 등) → 재시도 없이 즉시 VoiceProcessingException 발생")
    void nonRetryableError_noRetry_throwsImmediately() {
        when(elevenLabsClient.synthesize(any(), any()))
                .thenThrow(new VoiceProcessingException("ElevenLabs API 인증에 실패했습니다."));

        assertThatThrownBy(() -> provider.synthesize("테스트", null))
                .isInstanceOf(VoiceProcessingException.class)
                .hasMessageContaining("인증");

        verify(elevenLabsClient, times(1)).synthesize(any(), any());
        verify(elevenLabsClient, times(1)).getSubscriptionInfo();
    }

    @Test
    @DisplayName("쿼터 조회 자체가 실패해도 원래 예외는 그대로 전파 (graceful degradation)")
    void quotaQueryFails_originalExceptionStillPropagates() {
        when(elevenLabsClient.synthesize(any(), any()))
                .thenThrow(new VoiceProcessingException("ElevenLabs API 인증에 실패했습니다."));
        when(elevenLabsClient.getSubscriptionInfo())
                .thenThrow(new RuntimeException("쿼터 조회 API 실패"));

        assertThatThrownBy(() -> provider.synthesize("테스트", null))
                .isInstanceOf(VoiceProcessingException.class)
                .hasMessageContaining("인증");
    }

    @Test
    @DisplayName("voiceTone 매핑값(stability/similarity_boost)이 요청에 반영됨")
    void voiceTone_resolvesToVoiceParams() {
        when(elevenLabsClient.synthesize(any(), any())).thenReturn("audio".getBytes());

        provider.synthesize("테스트", VoiceSettings.builder().voiceTone("calm").build());

        verify(elevenLabsClient).synthesize(eq("sf8Bpb1IU97NI9BHSMRf"), argThat(req ->
            req.getVoiceSettings().getStability() == 0.7
        ));
    }
}
