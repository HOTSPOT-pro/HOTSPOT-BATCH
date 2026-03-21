package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.processor.impl;

import hotspot.batch.common.util.JsonConverter;
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

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UsageMetricsProcessor의 구현체
 * [최적화] nanoTime 정밀 측정 및 가독성 개선
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageMetricsProcessorImpl implements UsageMetricsProcessor {

    private final UsageAggregationService usageAggregationService;
    private final ComparisonCalculationService comparisonCalculationService;
    private final ReportInsightService reportInsightService;
    private final JsonConverter jsonConverter;

    private final AtomicInteger skipCount = new AtomicInteger(0);
    private final AtomicInteger calcCount = new AtomicInteger(0);
    private final AtomicLong aggTotalNs = new AtomicLong(0);
    private final AtomicLong compTotalNs = new AtomicLong(0);
    private final AtomicLong insightTotalNs = new AtomicLong(0);
    private final AtomicLong jsonTotalNs = new AtomicLong(0);

    @Override
    public WeeklyReport process(UsageMetricsAggregationInput input) throws Exception {
        if (input == null) return null;

        if (isEmptyUsage(input)) {
            skipCount.incrementAndGet();
            return buildEmptyReport(input);
        }

        // 1. 집계 (Aggregation)
        long t1 = System.nanoTime();
        UsageAggregationResult agg = usageAggregationService.aggregate(input);
        long t2 = System.nanoTime();
        aggTotalNs.addAndGet(t2 - t1);

        // 2. 비교 (Comparison)
        UsageComparisonResult comparison = comparisonCalculationService.calculate(input, agg);
        long t3 = System.nanoTime();
        compTotalNs.addAndGet(t3 - t2);

        // 3. 인사이트 (Insight)
        ReportInsight insight = reportInsightService.analyze(agg, comparison, input.lastWeekReport());
        long t4 = System.nanoTime();
        insightTotalNs.addAndGet(t4 - t3);

        // 4. 직렬화 (JSON Serialization)
        String scoreJson = jsonConverter.toJson(insight.scoreData());
        String summaryJson = jsonConverter.toJson(comparison.summaryData());
        String usageListJson = jsonConverter.toJson(comparison.usageListData());
        long t5 = System.nanoTime();
        jsonTotalNs.addAndGet(t5 - t4);

        int currentCalc = calcCount.incrementAndGet();

        /*
        if (currentCalc % 200 == 0) {
            log.info("[Processor-Detail] Count: {} | Skips: {} | Avg Times (ms): [Agg: {}, Comp: {}, Insight: {}, Json: {}]", 
                     currentCalc, 
                     skipCount.get(),
                     formatMs(aggTotalNs.get(), currentCalc),
                     formatMs(compTotalNs.get(), currentCalc),
                     formatMs(insightTotalNs.get(), currentCalc),
                     formatMs(jsonTotalNs.get(), currentCalc)
            );
        }
        */

        return WeeklyReport.builder()
                .weeklyReportId(input.basicInfo().weeklyReportId())
                .familyId(input.basicInfo().familyId())
                .subId(input.basicInfo().subId())
                .name(input.basicInfo().name())
                .weekStartDate(input.basicInfo().weekStartDate())
                .weekEndDate(input.basicInfo().weekEndDate())
                .totalUsage(agg.totalUsage())
                .summaryData(comparison.summaryData())
                .usageListData(comparison.usageListData())
                .scoreData(insight.scoreData())
                .tags(insight.tags())
                .reportStatus(ReportStatus.AGGREGATED.name())
                .scoreJson(scoreJson)
                .summaryJson(summaryJson)
                .usageListJson(usageListJson)
                .build();
    }

    private String formatMs(long totalNs, int count) {
        return String.format("%.3f", (double) totalNs / count / 1_000_000.0);
    }

    private boolean isEmptyUsage(UsageMetricsAggregationInput input) {
        return (input.weeklyAppUsage() == null || input.weeklyAppUsage().isEmpty()) &&
               (input.weeklyHourlyUsage() == null || input.weeklyHourlyUsage().isEmpty());
    }

    private WeeklyReport buildEmptyReport(UsageMetricsAggregationInput input) {
        return WeeklyReport.builder()
                .weeklyReportId(input.basicInfo().weeklyReportId())
                .familyId(input.basicInfo().familyId())
                .subId(input.basicInfo().subId())
                .name(input.basicInfo().name())
                .weekStartDate(input.basicInfo().weekStartDate())
                .weekEndDate(input.basicInfo().weekEndDate())
                .totalUsage(0L)
                .tags(Collections.emptyList())
                .reportStatus(ReportStatus.AGGREGATED.name())
                .scoreJson("{}")
                .summaryJson("{}")
                .usageListJson("[]")
                .build();
    }
}
