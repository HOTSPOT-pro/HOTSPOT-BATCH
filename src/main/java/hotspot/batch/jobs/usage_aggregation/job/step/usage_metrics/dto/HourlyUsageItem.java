package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 시간대별 합산 및 비교 사용량 정보 (차트 데이터용)
 */
@Builder
public record HourlyUsageItem(
    int hour,           // 시간대 (0, 3, 6, 9 ...)
    boolean isLateNight, // 심야 시간 여부
    boolean isStudyTime, // 집중 학습 시간 여부
    long thisWeek,      // 이번 주 사용량 (KB)
    long lastWeek       // 지난주 사용량 (KB)
) {}
