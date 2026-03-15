package hotspot.batch.jobs.llm_feedback.client.impl;

import hotspot.batch.common.exception.LlmJsonParsingException;
import hotspot.batch.common.util.JsonConverter;
import hotspot.batch.jobs.llm_feedback.client.LlmApiClient;
import hotspot.batch.jobs.llm_feedback.config.LlmProperties;
import hotspot.batch.jobs.llm_feedback.dto.AiFeedback;
import hotspot.batch.jobs.llm_feedback.dto.ChatCompletionRequest;
import hotspot.batch.jobs.llm_feedback.dto.ChatCompletionResponse;
import hotspot.batch.jobs.llm_feedback.dto.PromptMessages;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * OpenAI API 호출 실제 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!mock")
public class OpenAiApiClient implements LlmApiClient {

    private final WebClient llmWebClient;
    private final JsonConverter jsonConverter;
    private final LlmProperties properties;

    // 마크다운 코드 블록(```json ...)을 제거하기 위한 정규식 패턴
    private static final Pattern JSON_CLEANUP_PATTERN = Pattern.compile("(?s)```(?:json)?\\n?(.*?)\\n?```");

    /**
     * 프롬프트 메시지 쌍을 기반으로 OpenAI에 AI 피드백 생성을 요청함
     */
    @Override
    @RateLimiter(name = "llmFeedbackLimiter")
    @Retry(name = "llmFeedbackRetry")
    public Mono<AiFeedback> generateFeedback(PromptMessages messages) {
        var openaiProps = properties.openai();
        log.debug("Calling OpenAI API ({}) with system message length: {}", 
                openaiProps.model(), messages.systemMessage().length());

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(openaiProps.model())
                .messages(List.of(
                        // 프롬프트 파일에서 읽어온 시스템 메시지 적용
                        new ChatCompletionRequest.Message("system", messages.systemMessage()),
                        new ChatCompletionRequest.Message("user", messages.userMessage())
                ))
                .temperature(openaiProps.temperature())
                .max_tokens(openaiProps.maxTokens())
                .response_format(Map.of("type", "json_object"))
                .build();

        return llmWebClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + openaiProps.apiKey())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .map(response -> {
                    String content = response.choices().get(0).message().content();
                    String cleanJson = JSON_CLEANUP_PATTERN.matcher(content).replaceAll("$1").trim();
                    
                    try {
                        return jsonConverter.fromJson(cleanJson, AiFeedback.class);
                    } catch (Exception e) {
                        log.error("AI Feedback JSON Parsing Error. cleanJson: {}", cleanJson, e);
                        throw new LlmJsonParsingException("AI 피드백 JSON 파싱 실패", cleanJson, e);
                    }
                })
                .doOnError(e -> log.error("OpenAI API Call Failed for current item: {}", e.getMessage()));
    }
}
