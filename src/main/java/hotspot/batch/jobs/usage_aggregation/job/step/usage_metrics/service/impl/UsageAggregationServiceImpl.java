package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.UsageListDataFactory;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageAggregationResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageListData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.SummaryCalculationService;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.UsageAggregationService;
import lombok.RequiredArgsConstructor;

/**
 * 데이터 집계 계층: 요약 계산과 상세 리스트 생성을 통합하여 관리함
 */
@Service
@RequiredArgsConstructor
public class UsageAggregationServiceImpl implements UsageAggregationService {

    private final SummaryCalculationService summaryCalculationService;
    private final UsageListDataFactory usageListDataFactory;

    @Override
    public UsageAggregationResult aggregate(UsageMetricsAggregationInput input) {
        // 1. 이번 주 요약 데이터 계산
        SummaryData summaryData = summaryCalculationService.calculate(input);

        // 2. 이번 주 총 사용량 계산 (시간대별 Raw 데이터 기반)
        long totalUsage = input.weeklyHourlyUsage().stream()
            .flatMap(d -> d.hourlyUsage().values().stream())
            .mapToLong(Long::longValue)
            .sum();

        // 3. 차트용 상세 리스트 데이터 생성
        UsageListData usageListData = usageListDataFactory.create(input, totalUsage);

        return UsageAggregationResult.builder()
                .totalUsage(totalUsage)
                .summaryData(summaryData)
                .usageListData(usageListData)
                .build();
    }
}
