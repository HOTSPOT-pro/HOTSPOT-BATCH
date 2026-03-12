package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;
import java.util.Map;

/**
 * Redis/DB에서 조회된 원천 사용량 데이터
 */
public record UsageData(
    Long subId,
    List<DailyUsage> dailyUsageList,
    Map<String, Long> categoryUsageMap
) {}

/**
 * 일별 사용량 정보
 */
record DailyUsage(
    String date, // yyyy-MM-dd
    long usage,
    Map<Integer, Long> hourlyUsageMap // 0-23시별 사용량
) {}
