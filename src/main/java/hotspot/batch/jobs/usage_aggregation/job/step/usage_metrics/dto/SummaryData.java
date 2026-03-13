package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;
import lombok.Builder;

/**
 * 리포트의 요약 통계 데이터 묶음
 */
@Builder
public record SummaryData(
    DailySummaryItem dailySummary,
    HourlySummaryItem hourlySummary,
    List<CategorySummaryItem> categorySummary
) {}
