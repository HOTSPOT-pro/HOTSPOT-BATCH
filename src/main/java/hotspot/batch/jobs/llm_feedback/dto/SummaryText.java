package hotspot.batch.jobs.llm_feedback.dto;

import lombok.Builder;

/**
 * AI 피드백 - 요약 텍스트
 */
@Builder
public record SummaryText(
    String overall,
    String daily,
    String hourly,
    String category
) {}
