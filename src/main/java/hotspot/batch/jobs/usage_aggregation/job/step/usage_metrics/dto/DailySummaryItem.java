package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 일별 사용량 요약 (평일/주말 평균 및 전주 대비 비교)
 */
@Builder
public record DailySummaryItem(
    long weekdayAvg,
    long weekdayAvgDiff,
    double weekdayAvgChangeRate,
    long weekendAvg,
    long weekendAvgDiff,
    double weekendAvgChangeRate
) {}
