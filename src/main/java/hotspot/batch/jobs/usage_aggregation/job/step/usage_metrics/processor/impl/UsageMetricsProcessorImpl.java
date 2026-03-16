package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.processor.impl;

import hotspot.batch.jobs.usage_aggregation.job.ReportStatus;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ReportInsight;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageAggregationResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageComparisonResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReport;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.processor.UsageMetricsProcessor;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.ComparisonCalculationService;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.ReportInsightService;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.UsageAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * UsageMetricsProcessor의 구현체
 * 집계(Aggregation), 비교(Comparison), 분석(Insight) 3단계 레이어를 호출하여 리포트를 생성함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageMetricsProcessorImpl implements UsageMetricsProcessor {

    private final UsageAggregationService usageAggregationService; // 데이터 집계 (요약, 리스트, 총합)
    private final ComparisonCalculationService comparisonCalculationService; // 데이터 비교 (전주 대비)
    private final ReportInsightService reportInsightService; // 인사이트 분석 (태그, 점수)

    /**
     * 분석 파이프라인을 실행하여 최종 WeeklyReport를 생성함
     */
    @Override
    public WeeklyReport process(UsageMetricsAggregationInput input) throws Exception {
        if (input == null) return null;

        // 1. 데이터 집계 계층 (이번 주 수치 가공 - lastWeek은 0으로 초기화됨)
        UsageAggregationResult agg = usageAggregationService.aggregate(input);

        // 2. 데이터 비교 계층 (지난주 vs 이번 주 병합 및 비교 수치 산출)
        // agg를 넘겨서 그 결과를 바탕으로 lastWeek 필드가 채워진 새로운 comparison을 얻음
        UsageComparisonResult comparison = comparisonCalculationService.calculate(input, agg);

        // 3. 인사이트 분석 계층 (비즈니스 로직 적용)
        ReportInsight insight = reportInsightService.analyze(agg, comparison, input.lastWeekReport());

        // 4. 최종 리포트 조립 및 반환 (비교 수치가 포함된 comparison의 데이터를 사용)
        return WeeklyReport.builder()
                .weeklyReportId(input.basicInfo().weeklyReportId())
                .familyId(input.basicInfo().familyId())
                .subId(input.basicInfo().subId())
                .name(input.basicInfo().name())
                .weekStartDate(input.basicInfo().weekStartDate())
                .weekEndDate(input.basicInfo().weekEndDate())
                .totalUsage(agg.totalUsage())
                .summaryData(comparison.summaryData()) // 비교 수치가 포함된 데이터
                .usageListData(comparison.usageListData()) // lastWeek이 포함된 데이터
                .scoreData(insight.scoreData())
                .tags(insight.tags())
                .reportStatus(ReportStatus.AGGREGATED.name())
                .build();
    }
}
