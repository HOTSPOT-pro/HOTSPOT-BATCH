package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;

/**
 * 특정 날짜의 앱별 사용량 리스트
 */
public record DailyAppUsage(
    String date, // yyyy-MM-dd
    List<AppUsage> appUsageList
) {}
