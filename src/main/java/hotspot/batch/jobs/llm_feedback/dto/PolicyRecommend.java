package hotspot.batch.jobs.llm_feedback.dto;

import lombok.Builder;

/**
 * AI 피드백 - 정책 추천 항목
 */
@Builder
public record PolicyRecommend(
    String title,
    String description,
    String reason
) {}
