package hotspot.batch.jobs.llm_feedback.dto;

import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * OpenAI Chat Completion API 요청 DTO
 * - response_format 추가로 순수 JSON 응답 강제
 */
@Builder
public record ChatCompletionRequest(
    String model,
    List<Message> messages,
    Double temperature,
    Map<String, String> response_format
) {
    public record Message(
        String role,
        String content
    ) {}
}
