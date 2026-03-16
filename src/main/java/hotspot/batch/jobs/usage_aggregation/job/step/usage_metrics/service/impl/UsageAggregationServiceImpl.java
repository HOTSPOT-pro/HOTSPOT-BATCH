package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import hotspot.batch.common.util.UsageCalculator;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.*;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.AppCategoryCache;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.UsageAggregationService;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 데이터 집계 계층: 이번 주 Raw 데이터를 분석하여 리포트에 필요한 모든 수치 지표를 생성함
 */
@Service
@RequiredArgsConstructor
public class UsageAggregationServiceImpl implements UsageAggregationService {

    private static final Logger log = LoggerFactory.getLogger(UsageAggregationServiceImpl.class);

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
        log.debug("UsageAggregationService.aggregate: Processing input for subId={} weekStartDate={}",
                  input.basicInfo().subId(), input.basicInfo().weekStartDate());
        log.debug("  weeklyAppUsage size: {}", input.weeklyAppUsage().size());
        log.debug("  weeklyHourlyUsage size: {}", input.weeklyHourlyUsage().size());

        // 1. 이번 주 총 사용량 계산
        long totalUsage = calculateTotalUsage(input);
        log.debug("  Calculated totalUsage: {}", totalUsage);

        // 2. 요약 데이터(SummaryData) 생성
        SummaryData summaryData = createSummaryData(input);
        log.debug("  Calculated summaryData: weekdayAvg={}, weekendAvg={}, lateNightUsage={}, studyTimeUsage={}",
                  summaryData.dailySummary().weekdayAvg(), summaryData.dailySummary().weekendAvg(),
                  summaryData.hourlySummary().lateNightUsage(), summaryData.hourlySummary().studyTimeUsage());

        // 3. 상세 리스트(UsageListData) 생성
        UsageListData usageListData = createUsageListData(input, totalUsage);
        log.debug("  Calculated usageListData: totalUsage={}, dailyUsageListSize={}, hourlyUsageListSize={}",
                  usageListData.totalUsage(), usageListData.dailyUsageList().size(), usageListData.hourlyUsageList().size());

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

        return DailySummaryItem.builder()
                .weekdayAvg(weekdayAvg)
                .weekendAvg(weekendAvg)
                .build();
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

        return HourlySummaryItem.builder()
                .lateNightUsage(lateNight)
                .studyTimeUsage(studyTime)
                .build();
    }

    /**
     * 모든 앱 사용량을 카테고리별로 그룹화하여 전체 대비 비중(%)을 계산함
     */
    /**
     * 차트 시각화에 필요한 일별, 시간대별, 카테고리별 상세 리스트 데이터를 가공함
     * 카테고리 데이터는 이번 주 사용량과 비중을 한꺼번에 계산하여 통합 구조로 담음
     */
    private UsageListData createUsageListData(UsageMetricsAggregationInput input, long totalUsage) {
        // 1. 카테고리별 합산 데이터 생성
        Map<String, Long> categoryMap = aggregateCategory(input);
        long totalCategoryUsage = categoryMap.values().stream().mapToLong(Long::longValue).sum();

        // 2. 이번 주 카테고리 리스트 가공 (사용량 및 비중 계산)
        List<CategoryUsageItem> thisWeekCategoryList = categoryMap.entrySet().stream()
                .map(e -> CategoryUsageItem.builder()
                        .category(e.getKey())
                        .usage(e.getValue())
                        .percent(totalCategoryUsage > 0 ? round((e.getValue() / (double) totalCategoryUsage) * 100.0) : 0.0)
                        .build()
                )
                .sorted(Comparator.comparingLong(CategoryUsageItem::usage).reversed())
                .toList();

        return UsageListData.builder()
                .totalUsage(totalUsage)
                // 3. 일별 사용량 매칭 및 가공
                .dailyUsageList(input.weeklyAppUsage().stream()
                        .map(d -> DailyUsageItem.builder()
                                .date(d.date())
                                .day(LocalDate.parse(d.date()).getDayOfWeek().name())
                                .thisWeek(d.appUsageList().stream().mapToLong(a -> UsageCalculator.gbToKb(a.usedGb())).sum())
                                .lastWeek(0L) // Comparison 단계에서 채움
                                .build()
                        )
                        .sorted(Comparator.comparing(DailyUsageItem::date)).toList())
                // 4. 시간대별 사용량 매칭 및 가공
                .hourlyUsageList(aggregateHourly(input).entrySet().stream()
                        .map(e -> HourlyUsageItem.builder()
                                .hour(e.getKey())
                                .isLateNight(e.getKey() >= LATE_NIGHT_START && e.getKey() <= LATE_NIGHT_END)
                                .isStudyTime(e.getKey() >= STUDY_TIME_START && e.getKey() <= STUDY_TIME_END)
                                .thisWeek(e.getValue())
                                .lastWeek(0L) // Comparison 단계에서 채움
                                .build()
                        )
                        .sorted(Comparator.comparingInt(HourlyUsageItem::hour)).toList())
                // 5. 통합 카테고리 컨테이너 조립 (지난주 및 비교는 다음 단계에서 채움)
                .categoryUsageList(CategoryUsageListContainer.builder()
                        .thisWeek(thisWeekCategoryList)
                        .lastWeek(new ArrayList<>())
                        .comparison(new ArrayList<>())
                        .build())
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
