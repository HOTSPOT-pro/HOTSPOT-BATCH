package hotspot.batch.jobs.llm_feedback.dto;

import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * OpenAI Chat Completion API 요청 DTO
 * - response_format 추가로 순수 JSON 응답 강제
 * - max_tokens 추가로 답변 길이 제한
 */
@Builder
public record ChatCompletionRequest(
    String model,
    List<Message> messages,
    Double temperature,
    Map<String, String> response_format,
    Integer max_tokens // 출력 토큰 수 제한 필드 추가
) {
    public record Message(
        String role,
        String content
    ) {}
}
