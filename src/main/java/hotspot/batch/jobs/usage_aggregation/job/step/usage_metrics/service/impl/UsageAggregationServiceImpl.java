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

import hotspot.batch.common.util.UsageCalculator;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.CategorySummaryItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.CategoryUsageItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.DailyAppUsage;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.DailySummaryItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.DailyUsageItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.HourlySummaryItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.HourlyUsageItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageAggregationResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageListData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.AppCategoryCache;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.UsageAggregationService;
import lombok.RequiredArgsConstructor;

/**
 * 데이터 집계 계층: 이번 주 Raw 데이터를 분석하여 리포트에 필요한 모든 수치 지표를 생성함
 */
@Service
@RequiredArgsConstructor
public class UsageAggregationServiceImpl implements UsageAggregationService {

    private final AppCategoryCache appCategoryCache;

    // 시간대 정의 상수
    private static final int LATE_NIGHT_START = 0;
    private static final int LATE_NIGHT_END = 6;
    private static final int STUDY_TIME_START = 9;
    private static final int STUDY_TIME_END = 18;

    /**
     * 이번 주 총 사용량, 요약 통계, 상세 리스트를 한 번에 집계함
     */
    @Override
    public UsageAggregationResult aggregate(UsageMetricsAggregationInput input) {
        // 1. 이번 주 총 사용량 계산
        long totalUsage = calculateTotalUsage(input);

        // 2. 요약 데이터(SummaryData) 생성
        SummaryData summaryData = createSummaryData(input);

        // 3. 상세 리스트(UsageListData) 생성
        UsageListData usageListData = createUsageListData(input, totalUsage);

        return UsageAggregationResult.builder()
                .totalUsage(totalUsage)
                .summaryData(summaryData)
                .usageListData(usageListData)
                .build();
    }

    /**
     * 3시간 단위 시간대별 데이터를 모두 합산하여 이번 주 전체 사용량을 구함
     */
    private long calculateTotalUsage(UsageMetricsAggregationInput input) {
        return input.weeklyHourlyUsage().stream()
                .flatMap(d -> d.hourlyUsage().values().stream())
                .mapToLong(Long::longValue)
                .sum();
    }

    /**
     * 일별, 시간대별, 카테고리별 요약 정보를 담은 SummaryData 객체 생성함
     */
    private SummaryData createSummaryData(UsageMetricsAggregationInput input) {
        return SummaryData.builder()
                .dailySummary(calculateDailySummary(input))
                .hourlySummary(calculateHourlySummary(input))
                .categorySummary(calculateCategorySummary(input))
                .build();
    }

    /**
     * 평일과 주말을 구분하여 각각의 일일 평균 사용량을 계산함
     */
    private DailySummaryItem calculateDailySummary(UsageMetricsAggregationInput input) {
        List<DailyAppUsage> usage = input.weeklyAppUsage();
        if (usage == null || usage.isEmpty()) return DailySummaryItem.builder().build();

        Map<Boolean, List<Long>> partitioned = usage.stream()
                .collect(Collectors.partitioningBy(
                        d -> isWeekend(LocalDate.parse(d.date())),
                        Collectors.mapping(
                                d -> d.appUsageList().stream()
                                        .mapToLong(a -> UsageCalculator.gbToKb(a.usedGb())).sum(),
                                Collectors.toList()
                        )
                ));

        long weekdayAvg = getAverage(partitioned.get(false));
        long weekendAvg = getAverage(partitioned.get(true));

        return new DailySummaryItem(weekdayAvg, weekendAvg);
    }

    /**
     * 심야 시간대와 집중 학습 시간대에 해당하는 사용량을 각각 합산함
     */
    private HourlySummaryItem calculateHourlySummary(UsageMetricsAggregationInput input) {
        Map<Integer, Long> aggregated = aggregateHourly(input);
        long lateNight = aggregated.entrySet().stream()
                .filter(e -> e.getKey() >= LATE_NIGHT_START && e.getKey() <= LATE_NIGHT_END)
                .mapToLong(Map.Entry::getValue).sum();
        long studyTime = aggregated.entrySet().stream()
                .filter(e -> e.getKey() >= STUDY_TIME_START && e.getKey() <= STUDY_TIME_END)
                .mapToLong(Map.Entry::getValue).sum();

        return new HourlySummaryItem(lateNight, studyTime);
    }

    /**
     * 모든 앱 사용량을 카테고리별로 그룹화하여 전체 대비 비중(%)을 계산함
     */
    private List<CategorySummaryItem> calculateCategorySummary(UsageMetricsAggregationInput input) {
        Map<String, Long> categoryMap = aggregateCategory(input);
        long total = categoryMap.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) return Collections.emptyList();

        return categoryMap.entrySet().stream()
                .map(e -> new CategorySummaryItem(e.getKey(), round((e.getValue() / (double) total) * 100.0)))
                .sorted(Comparator.comparingDouble(CategorySummaryItem::percent).reversed())
                .toList();
    }

    /**
     * 차트 시각화에 필요한 일별, 시간대별, 카테고리별 상세 리스트 데이터를 가공함
     */
    private UsageListData createUsageListData(UsageMetricsAggregationInput input, long totalUsage) {
        return UsageListData.builder()
                .totalUsage(totalUsage)
                .dailyUsageList(input.weeklyAppUsage().stream()
                        .map(d -> new DailyUsageItem(d.date(), LocalDate.parse(d.date()).getDayOfWeek().name(),
                                d.appUsageList().stream().mapToLong(a -> UsageCalculator.gbToKb(a.usedGb())).sum()))
                        .sorted(Comparator.comparing(DailyUsageItem::date)).toList())
                .hourlyUsageList(aggregateHourly(input).entrySet().stream()
                        .map(e -> new HourlyUsageItem(e.getKey(), 
                                e.getKey() >= LATE_NIGHT_START && e.getKey() <= LATE_NIGHT_END,
                                e.getKey() >= STUDY_TIME_START && e.getKey() <= STUDY_TIME_END, e.getValue()))
                        .sorted(Comparator.comparingInt(HourlyUsageItem::startHour)).toList())
                .categoryUsageList(aggregateCategory(input).entrySet().stream()
                        .map(e -> new CategoryUsageItem(e.getKey(), e.getValue()))
                        .sorted(Comparator.comparingLong(CategoryUsageItem::usage).reversed()).toList())
                .build();
    }

    /**
     * 모든 날짜의 시간대별 데이터를 하나의 맵으로 합산함
     */
    private Map<Integer, Long> aggregateHourly(UsageMetricsAggregationInput input) {
        Map<Integer, Long> map = new HashMap<>();
        input.weeklyHourlyUsage().forEach(d -> d.hourlyUsage().forEach((h, u) -> map.merge(h, u, Long::sum)));
        return map;
    }

    /**
     * 모든 앱 사용 기록을 메모리 캐시를 참조하여 카테고리별로 합산함
     */
    private Map<String, Long> aggregateCategory(UsageMetricsAggregationInput input) {
        Map<String, Long> map = new HashMap<>();
        input.weeklyAppUsage().forEach(d -> d.appUsageList().forEach(a -> 
                map.merge(appCategoryCache.getCategoryName(a.appId()), UsageCalculator.gbToKb(a.usedGb()), Long::sum)));
        return map;
    }

    /**
     * 주어진 날짜가 주말(토, 일)인지 판별함
     */
    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    /**
     * 리스트 내 수치들의 산술 평균을 계산함
     */
    private long getAverage(List<Long> values) {
        return values.isEmpty() ? 0 : (long) values.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    /**
     * 소수점 둘째 자리까지 반올림함
     */
    private double round(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
