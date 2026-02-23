package com.example.echo.ai.service;

import com.example.echo.ai.client.OpenAIClient;
import com.example.echo.ai.dto.ChatCompletionRequest;
import com.example.echo.ai.dto.ChatCompletionResponse;
import com.example.echo.ai.exception.AIException;
import com.example.echo.context.domain.UserContext;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIServiceTest {

    @Mock
    private OpenAIClient openAIClient;

    @InjectMocks
    private AIService aiService;

    private UserContext context;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiService, "model", "gpt-4o-mini");
        ReflectionTestUtils.setField(aiService, "temperature", 0.7);
        ReflectionTestUtils.setField(aiService, "maxTokens", 1024);

        context = UserContext.builder()
                .userId(1L)
                .build();
    }

    // ===== generateGreeting 테스트 =====

    @Test
    @DisplayName("generateGreeting - 정상 케이스: system+user 메시지 2개 전달 및 응답 반환")
    void generateGreeting_success() {
        // Given
        String systemPrompt = "당신은 친근한 대화 상대입니다.";
        ChatCompletionResponse response = createMockResponse("안녕하세요! 오늘 하루는 어떠셨어요?");

        when(openAIClient.createChatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(response);

        // When
        String result = aiService.generateGreeting(systemPrompt, context);

        // Then
        assertThat(result).isEqualTo("안녕하세요! 오늘 하루는 어떠셨어요?");

        // 메시지 구조 검증
        ArgumentCaptor<ChatCompletionRequest> captor = ArgumentCaptor.forClass(ChatCompletionRequest.class);
        when(openAIClient.createChatCompletion(captor.capture())).thenReturn(response);
        aiService.generateGreeting(systemPrompt, context);

        ChatCompletionRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.getMessages()).hasSize(2);
        assertThat(capturedRequest.getMessages().get(0).getRole()).isEqualTo("system");
        assertThat(capturedRequest.getMessages().get(0).getContent()).isEqualTo(systemPrompt);
        assertThat(capturedRequest.getMessages().get(1).getRole()).isEqualTo("user");
    }

    @Test
    @DisplayName("generateGreeting - API 실패: FeignException → AIException 변환")
    void generateGreeting_apiFailure() {
        // Given
        String systemPrompt = "시스템 프롬프트";
        FeignException feignException = mock(FeignException.class);
        when(feignException.status()).thenReturn(500);
        when(feignException.getMessage()).thenReturn("Internal Server Error");

        when(openAIClient.createChatCompletion(any(ChatCompletionRequest.class)))
                .thenThrow(feignException);

        // When & Then
        assertThatThrownBy(() -> aiService.generateGreeting(systemPrompt, context))
                .isInstanceOf(AIException.class)
                .hasMessageContaining("AI 인사 생성 실패")
                .hasCause(feignException);
    }

    // ===== generateResponse 테스트 =====

    @Test
    @DisplayName("generateResponse - 정상 케이스: conversationPrompt가 system 메시지로 전달")
    void generateResponse_success() {
        // Given
        String conversationPrompt = "시스템 프롬프트 + 컨텍스트 + 히스토리 + 사용자 메시지";
        ChatCompletionResponse response = createMockResponse("좋은 질문이네요!");

        when(openAIClient.createChatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(response);

        // When
        String result = aiService.generateResponse(conversationPrompt);

        // Then
        assertThat(result).isEqualTo("좋은 질문이네요!");

        // 메시지 구조 검증
        ArgumentCaptor<ChatCompletionRequest> captor = ArgumentCaptor.forClass(ChatCompletionRequest.class);
        when(openAIClient.createChatCompletion(captor.capture())).thenReturn(response);
        aiService.generateResponse(conversationPrompt);

        ChatCompletionRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.getMessages()).hasSize(1);
        assertThat(capturedRequest.getMessages().get(0).getRole()).isEqualTo("system");
        assertThat(capturedRequest.getMessages().get(0).getContent()).isEqualTo(conversationPrompt);
    }

    @Test
    @DisplayName("generateResponse - API 실패: FeignException → AIException 변환")
    void generateResponse_apiFailure() {
        // Given
        String conversationPrompt = "대화 프롬프트";
        FeignException feignException = mock(FeignException.class);
        when(feignException.status()).thenReturn(429);
        when(feignException.getMessage()).thenReturn("Rate Limit Exceeded");

        when(openAIClient.createChatCompletion(any(ChatCompletionRequest.class)))
                .thenThrow(feignException);

        // When & Then
        assertThatThrownBy(() -> aiService.generateResponse(conversationPrompt))
                .isInstanceOf(AIException.class)
                .hasMessageContaining("AI 응답 생성 실패")
                .hasCause(feignException);
    }

    // ===== extractContent 테스트 (private 메서드를 generateResponse를 통해 간접 테스트) =====

    @Test
    @DisplayName("extractContent - 빈 응답: response가 null이거나 choices가 비어있을 때 빈 문자열 반환")
    void extractContent_emptyResponse() {
        // Given - null choices
        ChatCompletionResponse nullChoicesResponse = mock(ChatCompletionResponse.class);
        when(nullChoicesResponse.getChoices()).thenReturn(null);

        when(openAIClient.createChatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(nullChoicesResponse);

        // When
        String result1 = aiService.generateResponse("프롬프트");

        // Then
        assertThat(result1).isEmpty();

        // Given - empty choices list
        ChatCompletionResponse emptyChoicesResponse = mock(ChatCompletionResponse.class);
        when(emptyChoicesResponse.getChoices()).thenReturn(Collections.emptyList());

        when(openAIClient.createChatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(emptyChoicesResponse);

        // When
        String result2 = aiService.generateResponse("프롬프트");

        // Then
        assertThat(result2).isEmpty();
    }

    /**
     * ChatCompletionResponse 객체를 mock으로 구성
     * (NoArgsConstructor만 있어 setter/builder 없으므로 mock 사용)
     */
    private ChatCompletionResponse createMockResponse(String content) {
        ChatCompletionResponse.Message message = mock(ChatCompletionResponse.Message.class);
        when(message.getContent()).thenReturn(content);

        ChatCompletionResponse.Choice choice = mock(ChatCompletionResponse.Choice.class);
        when(choice.getMessage()).thenReturn(message);

        ChatCompletionResponse response = mock(ChatCompletionResponse.class);
        when(response.getChoices()).thenReturn(List.of(choice));

        return response;
    }
}
