package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;

/**
 * Reader에서 필요한 모든 원천 데이터를 벌크로 조합한 입력 객체
 */
public record UsageMetricsAggregationInput(
    ReportBasicInfo basicInfo,
    UsageData thisWeekUsage,             // 이번 주 전체 사용량
    List<DailyAppUsage> weeklyAppUsage,  // 이번 주 일별/앱별 상세 사용량
    List<DailyHourlyUsage> weeklyHourlyUsage, // 이번 주 일별/시간대별 상세 사용량 (추가)
    WeeklyReportSnapshot lastWeekReport  // 지난주 DB 스냅샷
) {}
