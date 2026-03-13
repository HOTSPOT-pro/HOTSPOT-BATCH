package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import hotspot.batch.jobs.usage_aggregation.job.ReportTag;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ReportInsight;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ScoreResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageAggregationResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageComparisonResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.ReportInsightService;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.ScoreCalculationService;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.TagGenerationService;
import lombok.RequiredArgsConstructor;

/**
 * 인사이트 분석 계층: 태그 생성과 점수 계산을 통합하여 관리함
 */
@Service
@RequiredArgsConstructor
public class ReportInsightServiceImpl implements ReportInsightService {

    private final TagGenerationService tagGenerationService;
    private final ScoreCalculationService scoreCalculationService;

    @Override
    public ReportInsight analyze(UsageAggregationResult agg, UsageComparisonResult comparison) {
        // 1. 인사이트 태그 생성
        List<ReportTag> tagList = tagGenerationService.generate(
                agg.summaryData(), comparison, agg.totalUsage());

        // 2. 정밀 점수 계산
        ScoreResult scoreResult = scoreCalculationService.calculate(
                agg.summaryData(), comparison, agg.totalUsage());

        return ReportInsight.builder()
                .scoreResult(scoreResult)
                .tags(tagList.stream().map(ReportTag::name).toList())
                .build();
    }
}
