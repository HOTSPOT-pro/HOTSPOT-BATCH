package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageAggregationResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;

/**
 * Raw 사용량 데이터를 리포트에 필요한 수치 데이터(요약, 상세 리스트, 총합)로 집계하는 서비스
 */
public interface UsageAggregationService {

    /**
     * 이번 주 Raw 데이터를 분석하여 통합 집계 결과를 생성함
     */
    UsageAggregationResult aggregate(UsageMetricsAggregationInput input);
}
