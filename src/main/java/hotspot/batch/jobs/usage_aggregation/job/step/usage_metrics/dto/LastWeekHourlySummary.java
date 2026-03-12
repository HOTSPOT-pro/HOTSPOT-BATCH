package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 지난주 시간대별 사용량 요약 (심야/학습 시간)
 */
@Builder
public record LastWeekHourlySummary(
    long lateNightUsage,
    long studyTimeUsage
) {}
