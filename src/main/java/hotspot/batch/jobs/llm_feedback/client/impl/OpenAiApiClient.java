package hotspot.batch.jobs.llm_feedback.client.impl;

import hotspot.batch.jobs.llm_feedback.client.LlmApiClient;
import hotspot.batch.jobs.llm_feedback.dto.AiFeedback;
import hotspot.batch.jobs.llm_feedback.dto.LlmFeedbackWeeklyReport;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * OpenAI API 호출을 담당하는 구현체
 * Resilience4j를 통해 Rate Limiting 및 Retry 적용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiApiClient implements LlmApiClient {

    private final WebClient llmWebClient;

    @Override
    @RateLimiter(name = "llmFeedbackLimiter")
    @Retry(name = "llmFeedbackRetry")
    public Mono<AiFeedback> generateFeedback(LlmFeedbackWeeklyReport report) {
        log.debug("Calling OpenAI API for weeklyReportId: {}", report.weeklyReportId());

        // 실제 API 호출 로직 (추후 프롬프트와 함께 구체화)
        // 현재는 WebClient 비동기 요청의 예시 구조만 작성
        return llmWebClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(createRequest(report)) // 추후 구현
                .retrieve()
                .bodyToMono(AiFeedback.class)
                .doOnError(e -> log.error("LLM API Call Failed for reportId: {}", report.weeklyReportId(), e));
    }

    private Object createRequest(LlmFeedbackWeeklyReport report) {
        // TODO: OpenAI API 스펙에 맞는 요청 객체 생성 (Model, Messages 등)
        return new Object();
    }
}
