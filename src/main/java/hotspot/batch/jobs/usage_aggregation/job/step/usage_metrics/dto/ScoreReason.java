package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 점수 가감 사유를 담는 DTO
 */
@Builder
public record ScoreReason(
    int value,     // 가감된 점수 (예: -10, +5)
    String reason  // 사유 (예: "심야 사용 과다", "학습 비중 높음")
) {}
