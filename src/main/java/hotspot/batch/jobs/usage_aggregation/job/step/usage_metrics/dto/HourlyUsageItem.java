package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 시간대별 합산 사용량 정보 (차트 데이터용)
 */
@Builder
public record HourlyUsageItem(
    int startHour,      // 시작 시간 (0~21)
    boolean isLateNight, // 심야 시간 여부
    boolean isStudyTime, // 집중 학습 시간 여부
    long totalUsage     // 해당 시간대의 총 사용량 (KB)
) {}
