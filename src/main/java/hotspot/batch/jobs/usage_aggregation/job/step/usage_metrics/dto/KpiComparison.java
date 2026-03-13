package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 리포트 핵심 지표(KPI) 비교 데이터
 * 전체 사용량 & 점수 차이
 */
@Builder
public record KpiComparison(
    ComparisonValue totalUsage,
    int scoreDiff
) {}
