package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 시간대별 사용량 요약 (심야/집중학습 및 전주 대비 비교)
 */
@Builder
public record HourlySummaryItem(
    long lateNightUsage,
    long lateNightUsageDiff,
    double lateNightUsageChangeRate,
    long studyTimeUsage,
    long studyTimeUsageDiff,
    double studyTimeUsageChangeRate
) {}
