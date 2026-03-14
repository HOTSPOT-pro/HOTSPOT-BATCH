package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 일별 합산 사용량 정보 (차트 데이터용)
 */
@Builder
public record DailyUsageItem(
    String date,      // yyyy-MM-dd
    String day,       // MONDAY, TUESDAY ...
    long totalUsage   // 해당 일자의 총 사용량 (KB)
) {}
