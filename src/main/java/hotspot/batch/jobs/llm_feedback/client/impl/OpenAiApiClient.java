package hotspot.batch.jobs.llm_feedback.client.impl;

import hotspot.batch.common.util.JsonConverter;
import hotspot.batch.jobs.llm_feedback.client.LlmApiClient;
import hotspot.batch.jobs.llm_feedback.dto.AiFeedback;
import hotspot.batch.jobs.llm_feedback.dto.ChatCompletionRequest;
import hotspot.batch.jobs.llm_feedback.dto.ChatCompletionResponse;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * OpenAI API 호출 상세 구현체 (비용 최적화 및 안정성 보강 버전)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiApiClient implements LlmApiClient {

    private final WebClient llmWebClient;
    private final JsonConverter jsonConverter;

    @Value("${llm.openai.api-key:dummy-key}")
    private String apiKey;

    // 비용 최적화를 위한 gpt-4o-mini 모델 적용 (gpt-4 대비 훨씬 저렴함)
    private static final String MODEL = "gpt-4o-mini";

    @Override
    @RateLimiter(name = "llmFeedbackLimiter") // Resilience4j-reactor에 의해 Mono의 시퀀스 구독 시점에 작동함
    @Retry(name = "llmFeedbackRetry")
    public Mono<AiFeedback> generateFeedback(String prompt) {
        log.debug("Calling OpenAI API ({}) with prompt length: {}", MODEL, prompt.length());

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(MODEL)
                .messages(List.of(
                        // 시스템 메시지에 JSON 형식을 반드시 포함해야 함 (response_format 조건)
                        new ChatCompletionRequest.Message("system", 
                            "당신은 아동 스마트폰 사용 패턴 분석 전문가입니다. 반드시 JSON 구조로만 답변하세요."),
                        new ChatCompletionRequest.Message("user", prompt)
                ))
                .temperature(0.7)
                // OpenAI API에게 순수 JSON 객체 반환을 강제함
                .response_format(Map.of("type", "json_object"))
                .build();

        return llmWebClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .map(response -> {
                    String content = response.choices().get(0).message().content();
                    
                    // 만약 응답에 마크다운 기호(```json)가 섞여 있을 경우를 대비한 정제 로직
                    String cleanJson = content.replaceAll("(?s)```(?:json)?\n?(.*?)\n?```", "$1").trim();
                    
                    try {
                        return jsonConverter.fromJson(cleanJson, AiFeedback.class);
                    } catch (Exception e) {
                        log.error("AI Feedback JSON Parsing Error. cleanJson: {}", cleanJson, e);
                        throw new RuntimeException("AI 피드백 JSON 파싱 실패", e);
                    }
                })
                .doOnError(e -> log.error("OpenAI API Call Failed: {}", e.getMessage()));
    }
}
