package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.AppUsage;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.CategorySummaryItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.DailyAppUsage;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.DailyHourlyUsage;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.DailySummaryItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.HourlySummaryItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.AppCategoryCache;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.SummaryCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이번 주 사용량 데이터를 바탕으로 요약 통계를 계산하는 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryCalculationServiceImpl implements SummaryCalculationService {

    private final AppCategoryCache appCategoryCache;

    // 시간대별 정의
    private static final int LATE_NIGHT_START_HOUR = 0;
    private static final int LATE_NIGHT_END_HOUR = 6;
    private static final int STUDY_TIME_START_HOUR = 9;
    private static final int STUDY_TIME_END_HOUR = 18;

    /**
     * 전체 요약 계산을 위한 메인 진입점
     */
    @Override
    public SummaryData calculate(UsageMetricsAggregationInput input) {
        DailySummaryItem dailySummaryItem = calculateDailySummaryItem(input);
        HourlySummaryItem hourlySummaryItem = calculateHourlySummaryItem(input);
        List<CategorySummaryItem> categorySummaryItems = calculateCategorySummaryItems(input);

        return SummaryData.builder()
                .dailySummary(dailySummaryItem)
                .hourlySummary(hourlySummaryItem)
                .categorySummary(categorySummaryItems)
                .build();
    }

    /**
     * 일별 데이터를 분석하여 평일/주말 평균 사용량을 계산함
     */
    private DailySummaryItem calculateDailySummaryItem(UsageMetricsAggregationInput input) {
        List<DailyAppUsage> weeklyAppUsage = input.weeklyAppUsage();
        if (weeklyAppUsage == null || weeklyAppUsage.isEmpty()) {
            return DailySummaryItem.builder().build();
        }

        Map<LocalDate, Long> dailyTotalUsage = weeklyAppUsage.stream()
            .collect(Collectors.toMap(
                usage -> LocalDate.parse(usage.date()),
                usage -> usage.appUsageList().stream()
                    .mapToLong(app -> Math.round(app.usedGb() * 1024 * 1024))
                    .sum()
            ));

        Map<Boolean, List<Map.Entry<LocalDate, Long>>> groupedByWeekend = dailyTotalUsage.entrySet().stream()
            .collect(Collectors.partitioningBy(entry -> {
                DayOfWeek day = entry.getKey().getDayOfWeek();
                return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            }));

        List<Map.Entry<LocalDate, Long>> weekdays = groupedByWeekend.get(false);
        List<Map.Entry<LocalDate, Long>> weekends = groupedByWeekend.get(true);

        long weekdayTotal = weekdays.stream().mapToLong(Map.Entry::getValue).sum();
        long weekdayAvg = weekdays.isEmpty() ? 0 : weekdayTotal / weekdays.size();
        
        long weekendTotal = weekends.stream().mapToLong(Map.Entry::getValue).sum();
        long weekendAvg = weekends.isEmpty() ? 0 : weekendTotal / weekends.size();
        
        return DailySummaryItem.builder()
                .weekdayAvg(weekdayAvg)
                .weekendAvg(weekendAvg)
                .build();
    }

    /**
     * 시간대별 사용량 데이터를 분석하여 심야 사용량, 학습 시간 사용량 등을 계산함
     */
    private HourlySummaryItem calculateHourlySummaryItem(UsageMetricsAggregationInput input) {
        List<DailyHourlyUsage> weeklyHourlyUsage = input.weeklyHourlyUsage();
        if (weeklyHourlyUsage == null || weeklyHourlyUsage.isEmpty()) {
            return HourlySummaryItem.builder().build();
        }

        Map<Integer, Long> aggregatedHourlyUsage = new HashMap<>();
        weeklyHourlyUsage.stream()
                .flatMap(daily -> daily.hourlyUsage().entrySet().stream())
                .forEach(entry -> aggregatedHourlyUsage.merge(entry.getKey(), entry.getValue(), Long::sum));

        long lateNightUsage = aggregatedHourlyUsage.entrySet().stream()
                .filter(entry -> entry.getKey() >= LATE_NIGHT_START_HOUR && entry.getKey() <= LATE_NIGHT_END_HOUR)
                .mapToLong(Map.Entry::getValue)
                .sum();
        
        long studyTimeUsage = aggregatedHourlyUsage.entrySet().stream()
                .filter(entry -> entry.getKey() >= STUDY_TIME_START_HOUR && entry.getKey() <= STUDY_TIME_END_HOUR)
                .mapToLong(Map.Entry::getValue)
                .sum();

        return HourlySummaryItem.builder()
                .lateNightUsage(lateNightUsage)
                .studyTimeUsage(studyTimeUsage)
                .build();
    }

    /**
     * 주간 앱 사용량 데이터를 집계하여 카테고리별 사용량 비율(%)을 계산함
     */
    private List<CategorySummaryItem> calculateCategorySummaryItems(UsageMetricsAggregationInput input) {
        List<DailyAppUsage> weeklyAppUsage = input.weeklyAppUsage();
        if (weeklyAppUsage == null || weeklyAppUsage.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 카테고리(appId)별 주간 총 사용량 합산
        Map<Long, Double> usageByAppId = weeklyAppUsage.stream()
                .flatMap(daily -> daily.appUsageList().stream())
                .collect(Collectors.groupingBy(
                        AppUsage::appId,
                        Collectors.summingDouble(AppUsage::usedGb)
                ));

        // 2. 전체 사용량 합계 계산
        double totalUsage = usageByAppId.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalUsage == 0) return Collections.emptyList();

        // 3. 각 카테고리별 비율 계산
        return usageByAppId.entrySet().stream()
                .map(entry -> {
                    double percent = (entry.getValue() / totalUsage) * 100.0;
                    // AppCategoryCache를 통해 appId를 실제 카테고리 이름으로 변환
                    String categoryName = appCategoryCache.getCategoryName(entry.getKey());

                    return CategorySummaryItem.builder()
                            .category(categoryName)
                            .percent(BigDecimal.valueOf(percent).setScale(2, RoundingMode.HALF_UP).doubleValue())
                            .build();
                })
                .sorted(Comparator.comparingDouble(CategorySummaryItem::percent).reversed())
                .toList();
    }
}
