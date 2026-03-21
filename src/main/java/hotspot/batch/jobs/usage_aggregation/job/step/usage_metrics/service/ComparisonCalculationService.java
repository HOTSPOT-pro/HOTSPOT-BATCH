package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageAggregationResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageComparisonResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;

/**
 * 이번 주 집계 데이터와 지난주 리포트 스냅샷을 비교하여 증감 지표를 산출하는 서비스
 */
public interface ComparisonCalculationService {

    /**
     * 이번 주 집계 요약 데이터와 지난주 리포트 스냅샷을 비교하여 최종 비교 결과 DTO를 생성함
     * 
     * @param input 이번 주 Raw 데이터 및 지난주 스냅샷 정보가 포함된 통합 입력 객체
     * @param thisWeek 이미 계산된 이번 주 전체 집계 데이터 (중복 계산 방지)
     * @return 각 지표별 증감 수치와 퍼센트가 포함된 통합 비교 결과 객체
     */
    UsageComparisonResult calculate(UsageMetricsAggregationInput input, UsageAggregationResult thisWeek);
}
