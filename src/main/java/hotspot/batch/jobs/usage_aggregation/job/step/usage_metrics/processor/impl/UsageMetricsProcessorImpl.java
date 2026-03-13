package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.processor.impl;

import hotspot.batch.jobs.usage_aggregation.job.ReportStatus;
import hotspot.batch.jobs.usage_aggregation.job.ReportTag;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageComparisonResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReport;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.processor.UsageMetricsProcessor;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.ComparisonCalculationService;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.SummaryCalculationService;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.TagGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * UsageMetricsProcessor의 구현체
 * 각 비즈니스 연산 서비스를 순차적으로 호출하여 데이터 처리 파이프라인을 구성함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageMetricsProcessorImpl implements UsageMetricsProcessor {

    private final SummaryCalculationService summaryCalculationService;
    private final ComparisonCalculationService comparisonCalculationService;
    private final TagGenerationService tagGenerationService;
    // TODO: Score 서비스 주입 필요

    /**
     * Reader가 전달한 모든 재료(Input)를 받아 최종 리포트(WeeklyReport)를 조립함
     */
    @Override
    public WeeklyReport process(UsageMetricsAggregationInput input) throws Exception {
        if (input == null) return null;

        // 1. 이번 주 사용량 요약 (총합, 평균, 피크타임 등)
        SummaryData summaryData = summaryCalculationService.calculate(input);

        // 2. 지난주 대비 증감 계산 (이미 계산된 summaryData를 활용하여 중복 연산 방지)
        UsageComparisonResult comparisonResult = comparisonCalculationService.calculate(input, summaryData);
        
        // 3. 이번 주 총 사용량 산출 (KB 또는 분 단위)
        long totalUsage = comparisonResult.kpi().totalUsage().diff() + (input.lastWeekReport() != null ? input.lastWeekReport().totalUsage() : 0);

        // 4. 인사이트 태그 생성 (우선순위에 따라 최대 2개)
        List<ReportTag> tagList = tagGenerationService.generate(summaryData, comparisonResult, totalUsage);

        // 5. 점수 계산
        // TODO: TagScoreService.calculate(input, summaryData, comparisonResult);

        // 최종 WeeklyReport 객체 생성 및 반환
        return WeeklyReport.builder()
                .reportId(input.basicInfo().weeklyReportId())
                .totalUsage(totalUsage)
                .summaryData(summaryData)
                // .totalScore(...) // TagScoreService 결과
                .tags(tagList.stream().map(ReportTag::name).toList())
                // .usageListData(...) // 상세 리스트 데이터 구성 (추후 구현)
                .reportStatus(ReportStatus.AGGREGATED.name())
                .build();
    }
}
