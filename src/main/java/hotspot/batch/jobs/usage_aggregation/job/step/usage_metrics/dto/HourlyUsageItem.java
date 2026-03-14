package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Builder;

/**
 * 시간대별 합산 및 비교 사용량 정보 (차트 데이터용)
 */
@Builder
public record HourlyUsageItem(
    // DB에 저장된 옛날 이름(startHour, totalUsage)을 현재 이름으로 자동 매칭해서 읽어오기 위해 필요함
    @JsonAlias("startHour") int hour,           // 시간대 (0, 3, 6, 9 ...)
    boolean isLateNight, // 심야 시간 여부
    boolean isStudyTime, // 집중 학습 시간 여부
    @JsonAlias("totalUsage") long thisWeek,      // 이번 주 사용량 (KB)
    long lastWeek       // 지난주 사용량 (KB)
) {}
