package hotspot.batch.jobs.llm_feedback.dto;

import java.util.List;

/**
 * OpenAI Chat Completion API 응답 DTO
 */
public record ChatCompletionResponse(
    String id,
    String object,
    long created,
    String model,
    List<Choice> choices,
    Usage usage
) {
    public record Choice(
        Message message,
        String finish_reason,
        int index
    ) {}

    public record Message(
        String role,
        String content
    ) {}

    public record Usage(
        int prompt_tokens,
        int completion_tokens,
        int total_tokens
    ) {}
}
