package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;
import lombok.Builder;

/**
 * 이번 주와 지난주 사용량을 비교한 최종 집계 및 서빙용 결과물
 */
@Builder
public record UsageComparisonResult(
    KpiComparison kpi,
    SummaryData summaryData,
    UsageListData usageListData
) {}
