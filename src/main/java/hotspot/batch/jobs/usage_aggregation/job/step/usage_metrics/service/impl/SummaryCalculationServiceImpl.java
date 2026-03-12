package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.*;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.SummaryCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 이번 주 사용량 데이터를 바탕으로 요약 통계를 계산하는 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryCalculationServiceImpl implements SummaryCalculationService {

    @Override
    public SummaryData calculate(UsageMetricsAggregationInput input) {
        DailySummaryItem dailySummaryItem = calculateDailySummaryItem(input);
        HourlySummaryItem hourlySummaryItem = calculateHourlySummaryItem(input);
        List<CategorySummaryItem> categorySummary = calculateCategorySummary(input);

        return SummaryData.builder()
                .dailySummary(dailySummaryItem)
                .hourlySummary(hourlySummaryItem)
                .categorySummary(categorySummary)
                .build();
    }

    private DailySummaryItem calculateDailySummaryItem(UsageMetricsAggregationInput input) {
        // TODO: 일별(평일/주말) 평균, 피크 요일 등 계산 로직 구현
        return DailySummaryItem.builder().build(); // 임시 반환
    }

    private HourlySummaryItem calculateHourlySummaryItem(UsageMetricsAggregationInput input) {
        // TODO: 시간대별(피크/심야) 사용량 계산 로직 구현
        return HourlySummaryItem.builder().build(); // 임시 반환
    }

    private List<CategorySummaryItem> calculateCategorySummary(UsageMetricsAggregationInput input) {
        // TODO: 카테고리별 사용량 비율(percent) 계산 로직 구현
        return List.of(CategorySummaryItem.builder()
                .build());
    }
}
