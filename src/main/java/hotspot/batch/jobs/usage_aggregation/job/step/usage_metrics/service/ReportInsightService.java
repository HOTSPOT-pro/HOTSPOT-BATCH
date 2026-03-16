package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ReportInsight;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageAggregationResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageComparisonResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReportSnapshot;

/**
 * 가공된 수치 데이터를 분석하여 비즈니스 인사이트(태그, 점수)를 도출하는 서비스
 */
public interface ReportInsightService {

    /**
     * 집계 결과와 비교 결과를 바탕으로 통합 인사이트를 생성함
     */
    ReportInsight analyze(UsageAggregationResult agg, UsageComparisonResult comparison, WeeklyReportSnapshot lastWeek);
}
