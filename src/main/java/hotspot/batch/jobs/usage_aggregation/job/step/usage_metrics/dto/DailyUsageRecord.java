package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.Map;

/**
 * 하루 단위의 모든 사용량 정보
 */
public record DailyUsageRecord(
    String date,               // yyyy-MM-dd
    String dayOfWeek,          // MONDAY, TUESDAY...
    Map<String, Long> categoryUsage, // { "study": 20000, "game": 15000 ... }
    Map<Integer, Long> hourlyUsage   // { 0: 5000, 3: 10000 ... } (3시간 단위)
) {}
