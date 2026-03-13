package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

/**
 * 개별 앱의 사용량 정보
 */
public record AppUsage(
    Long appId,
    double usedGb
) {}
