package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 일별 합산 및 비교 사용량 정보 (차트 데이터용)
 */
@Builder
public record DailyUsageItem(
    String date,      // yyyy-MM-dd
    String day,       // MONDAY, TUESDAY ...
    long thisWeek,    // 이번 주 사용량 (KB)
    long lastWeek     // 지난주 사용량 (KB)
) {}
