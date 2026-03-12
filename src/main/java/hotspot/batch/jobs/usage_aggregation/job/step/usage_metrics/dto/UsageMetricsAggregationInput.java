package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;

/**
 * Reader에서 필요한 모든 원천 데이터를 벌크로 조합한 입력 객체
 */
public record UsageMetricsAggregationInput(
    ReportBasicInfo basicInfo,
    List<DailyAppUsage> weeklyAppUsage,
    List<DailyHourlyUsage> weeklyHourlyUsage,
    WeeklyReportSnapshot lastWeekReport
) {}
