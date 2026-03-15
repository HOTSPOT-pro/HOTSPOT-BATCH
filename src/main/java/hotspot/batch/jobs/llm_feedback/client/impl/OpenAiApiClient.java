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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * OpenAI API 호출 실제 구현체
 * - 비용 최적화 (gpt-4o-mini)
 * - 안정성 보장 (Resilience4j + WebClient)
 * - JSON 무결성 (response_format + 정규식 클리닝)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!mock")
public class OpenAiApiClient implements LlmApiClient {

    private final WebClient llmWebClient;
    private final JsonConverter jsonConverter;

    @Value("${llm.openai.api-key:dummy-key}")
    private String apiKey;

    @Value("${llm.openai.model}")
    private String model;

    @Value("${llm.openai.temperature}")
    private double temperature;

    @Value("${llm.openai.max-tokens}")
    private int maxTokens;

    /**
     * 프롬프트를 기반으로 OpenAI에 AI 피드백 생성을 요청함 (Reactive/비동기)
     * @param prompt 최종 완성된 프롬프트 문자열
     * @return 파싱이 완료된 AiFeedback 객체 (Mono)
     */
    @Override
    @RateLimiter(name = "llmFeedbackLimiter") // 시퀀스 구독 시점에 작동하여 RPM 제어
    @Retry(name = "llmFeedbackRetry")          // 일시적 오류 시 지수 백오프 기반 재시도
    public Mono<AiFeedback> generateFeedback(String prompt) {
        log.debug("Calling OpenAI API ({}) with prompt length: {}", model, prompt.length());

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .messages(List.of(
                        // 시스템 메시지에 JSON 반환 지침을 포함해야 response_format 기능이 완벽히 작동함
                        new ChatCompletionRequest.Message("system",
                            "당신은 아동 스마트폰 사용 패턴 분석 전문가입니다. 반드시 JSON 구조로만 답변하세요."),
                        new ChatCompletionRequest.Message("user", prompt)
                ))
                .temperature(temperature)
                .max_tokens(maxTokens) // YAML에서 주입받은 최대 토큰 수 설정
                .response_format(Map.of("type", "json_object"))
                .build();

        return llmWebClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .map(response -> {
                    // 응답에서 첫 번째 choice의 content(JSON 문자열)를 추출
                    String content = response.choices().get(0).message().content();
                    
                    // GPT 모델이 마크다운 코드 블록(```json ...)을 포함하더라도 순수 JSON만 추출하도록 정제
                    String cleanJson = content.replaceAll("(?s)```(?:json)?\\n?(.*?)\\n?```", "$1").trim();
                    
                    try {
                        // 추출된 JSON 문자열을 AiFeedback DTO로 변환
                        return jsonConverter.fromJson(cleanJson, AiFeedback.class);
                    } catch (Exception e) {
                        log.error("AI Feedback JSON Parsing Error. cleanJson: {}", cleanJson, e);
                        throw new RuntimeException("AI 피드백 JSON 파싱 실패", e);
                    }
                })
                .doOnError(e -> log.error("OpenAI API Call Failed for current item: {}", e.getMessage()));
    }
}
