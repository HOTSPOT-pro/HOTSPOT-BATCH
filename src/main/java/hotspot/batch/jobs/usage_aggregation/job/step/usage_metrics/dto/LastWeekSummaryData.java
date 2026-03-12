package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;
import lombok.Builder;

/**
 * 지난주 리포트의 요약 통계 데이터 묶음
 */
@Builder
public record LastWeekSummaryData(
    LastWeekDailySummary dailySummary,
    LastWeekHourlySummary hourlySummary,
    List<LastWeekCategorySummary> categorySummary
) {}
