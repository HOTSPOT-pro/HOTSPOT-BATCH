package hotspot.batch.jobs.llm_feedback.dto;

/**
 * 프롬프트 템플릿에서 추출된 시스템 메시지와 사용자 메시지 쌍
 */
public record PromptMessages(
    String systemMessage,
    String userMessage
) {}
