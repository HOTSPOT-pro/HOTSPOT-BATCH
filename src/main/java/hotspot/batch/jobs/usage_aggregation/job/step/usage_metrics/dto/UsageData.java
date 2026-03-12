package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;

/**
 * Redis에서 조회된 이번 주 원천 사용량 데이터 (Input 데이터)
 * Processor에서 일별/시간대별/카테고리별 집계를 수행하기 위한 재료가 됨
 */
public record UsageData(
    Long subId,
    List<DailyUsageRecord> dailyUsageList
) {}
