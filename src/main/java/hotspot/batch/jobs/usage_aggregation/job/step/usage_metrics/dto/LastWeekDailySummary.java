package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 지난주 일별 사용량 요약 (평일/주말 평균)
 */
@Builder
public record LastWeekDailySummary(
    long weekdayAvg,
    long weekendAvg
) {}
