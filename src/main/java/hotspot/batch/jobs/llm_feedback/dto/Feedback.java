package hotspot.batch.jobs.llm_feedback.dto;

import lombok.Builder;

/**
 * AI 피드백 - 부모/자녀 대상 피드백 메시지
 */
@Builder
public record Feedback(
    String toParent,
    String toChild
) {}
