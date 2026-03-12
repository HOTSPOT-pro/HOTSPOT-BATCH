package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

/**
 * Reader에서 (리포트 기본 정보, 이번주 사용량, 지난주 리포트 스냅샷)을 Bulk Read
 * Processor는 이 객체를 받아 I/O 없이 순수 연산만 수행함
 */
public record UsageMetricsAggregationInput(
    ReportBasicInfo basicInfo,           // 기본 정보 (reportId, subId 등)
    UsageData thisWeekUsage,             // 이번 주 Redis 데이터
    WeeklyReportSnapshot lastWeekReport  // 지난주 DB 스냅샷
) {}
