package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.processor.impl;

import hotspot.batch.jobs.usage_aggregation.job.ReportStatus;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReport;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.processor.UsageMetricsProcessor;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.SummaryCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * UsageMetricsProcessor의 구현체
 * 각 비즈니스 연산 서비스를 순차적으로 호출하여 데이터 처리 파이프라인을 구성함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageMetricsProcessorImpl implements UsageMetricsProcessor {

    private final SummaryCalculationService summaryCalculationService;
    // TODO: Comparison, Tag, Score 서비스 주입 필요

    /**
     * Reader가 전달한 모든 재료(Input)를 받아 최종 리포트(WeeklyReport)를 조립함
     */
    @Override
    public WeeklyReport process(UsageMetricsAggregationInput input) throws Exception {
        if (input == null) return null;

        // 1. 이번 주 사용량 요약 (총합, 평균, 피크타임 등)
        SummaryData summaryData = summaryCalculationService.calculate(input);

        // 2. 지난주 대비 증감 계산
        // TODO: ComparisonCalculationService.calculate(input, summaryData);

        // 3. 태그 및 점수 계산
        // TODO: TagScoreService.calculate(input, summaryData, comparisonResult);

        // 최종 WeeklyReport 객체 생성
        return WeeklyReport.builder()
                .reportId(input.basicInfo().weeklyReportId())
                // .totalUsage(...) // summaryData에서 추출
                // .totalScore(...) // TagScoreService 결과
                // .tags(...)       // TagScoreService 결과
                .summaryData(summaryData)
                // .usageListData(...) // UsageAggregationService 결과 (별도 서비스로 분리 필요)
                .reportStatus(ReportStatus.AGGREGATED.name())
                .build();
    }
}
