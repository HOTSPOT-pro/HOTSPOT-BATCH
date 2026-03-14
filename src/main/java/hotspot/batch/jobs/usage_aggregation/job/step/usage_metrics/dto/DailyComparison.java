package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 평일/주말 평균 사용량 비교 데이터
 */
@Builder
public record DailyComparison(
    ComparisonValue weekdayAvg,
    ComparisonValue weekendAvg
) {}
