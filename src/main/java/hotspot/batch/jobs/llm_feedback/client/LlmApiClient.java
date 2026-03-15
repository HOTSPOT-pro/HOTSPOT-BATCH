package hotspot.batch.jobs.llm_feedback.client;

import hotspot.batch.jobs.llm_feedback.dto.AiFeedback;
import hotspot.batch.jobs.llm_feedback.dto.PromptMessages;
import reactor.core.publisher.Mono;

/**
 * 외부 LLM API(OpenAI 등)와의 통신을 담당하는 Client 인터페이스
 */
public interface LlmApiClient {
    /**
     * 시스템/사용자 메시지 쌍을 기반으로 AI 피드백 생성을 요청함
     */
    Mono<AiFeedback> generateFeedback(PromptMessages messages);
}
