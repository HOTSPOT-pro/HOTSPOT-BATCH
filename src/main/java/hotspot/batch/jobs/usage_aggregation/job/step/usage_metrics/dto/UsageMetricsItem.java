package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

/**
 * Step2 reader가 읽어온 WeeklyReport 처리 대상을 담는 모델
 */
public record UsageMetricsItem(
        Long weeklyReportId,
        Long subId) {
}
