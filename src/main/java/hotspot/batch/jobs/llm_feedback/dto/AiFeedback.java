package hotspot.batch.jobs.llm_feedback.dto;

import java.util.List;
import lombok.Builder;

/**
 * AI 피드백 상세 구조 (최종 취합 DTO)
 */
@Builder
public record AiFeedback(
    SummaryText summaryText,
    List<String> keyInsights,
    Feedback feedback,
    List<PolicyRecommend> policyRecommendList
) {}
