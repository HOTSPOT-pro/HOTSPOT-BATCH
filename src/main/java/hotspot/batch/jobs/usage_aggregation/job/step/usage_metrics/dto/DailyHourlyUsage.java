package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.Map;

/**
 * 특정 날짜의 3시간 단위 시간대별 사용량 정보
 */
public record DailyHourlyUsage(
    String date, // yyyy-MM-dd
    Map<Integer, Long> hourlyUsage // { 0: 1234, 3: 5678 ... }
) {}
