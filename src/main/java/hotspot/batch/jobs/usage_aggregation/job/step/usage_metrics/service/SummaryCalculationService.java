package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;

/**
 * 이번 주 사용량 데이터를 바탕으로 요약 통계를 계산하는 서비스
 */
public interface SummaryCalculationService {
    /**
     * 원천 사용량 데이터를 분석하여 총합, 평균, 피크 정보를 포함한 Summary를 생성함
     */
    SummaryData calculate(UsageMetricsAggregationInput input);
}
