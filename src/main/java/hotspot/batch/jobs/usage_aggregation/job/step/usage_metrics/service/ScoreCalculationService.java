package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ScoreResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageComparisonResult;

/**
 * 이번 주 통계 및 전주 대비 변화를 바탕으로 최종 리포트 점수를 계산하는 서비스
 */
public interface ScoreCalculationService {

    /**
     * 분석 데이터를 바탕으로 5대 지표(사용량, 비중, 심야, 패턴, 개선도)를 정밀 분석하여 점수 산출
     * 
     * @param summary 이번 주 요약 통계
     * @param comparison 전주 대비 증감 통계
     * @param totalUsage 이번 주 총 사용량
     * @return 합산 점수, 등급, 가감 사유가 포함된 결과 객체
     */
    ScoreResult calculate(SummaryData summary, UsageComparisonResult comparison, long totalUsage);
}
