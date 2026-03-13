package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 이번 주 Raw 데이터를 집계한 통합 결과 객체
 */
@Builder
public record UsageAggregationResult(
    long totalUsage,
    SummaryData summaryData,
    UsageListData usageListData
) {}
