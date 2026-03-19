package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import org.springframework.stereotype.Service;

import hotspot.batch.common.util.UsageCalculator;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.*;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.AppCategoryCache;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.UsageAggregationService;
import lombok.RequiredArgsConstructor;

/**
 * 데이터 집계 계층: 이번 주 Raw 데이터를 분석하여 리포트에 필요한 모든 수치 지표를 생성함
 * [최적화] Stream 제거 및 전통적인 for-loop 사용, 중복 연산 제거
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

    @Override
    public UsageAggregationResult aggregate(UsageMetricsAggregationInput input) {
        // 1. 시간대별 데이터 합산 및 총 사용량 계산 (한 번의 루프로 처리)
        Map<Integer, Long> hourlyMap = new HashMap<>(24);
        long totalUsage = 0;
        for (DailyHourlyUsage d : input.weeklyHourlyUsage()) {
            for (Map.Entry<Integer, Long> entry : d.hourlyUsage().entrySet()) {
                long val = entry.getValue();
                hourlyMap.merge(entry.getKey(), val, Long::sum);
                totalUsage += val;
            }
        }

        // 2. 카테고리별 데이터 합산 및 일별 데이터 가공 (한 번의 루프로 처리)
        Map<String, Long> categoryMap = new HashMap<>();
        List<DailyUsageItem> dailyUsageList = new ArrayList<>(input.weeklyAppUsage().size());
        
        long totalWeekdayUsage = 0;
        long totalWeekendUsage = 0;
        int weekdayCount = 0;
        int weekendCount = 0;

        for (DailyAppUsage d : input.weeklyAppUsage()) {
            LocalDate date = LocalDate.parse(d.date());
            long dayUsage = 0;
            for (AppUsage a : d.appUsageList()) {
                long usageKb = UsageCalculator.gbToKb(a.usedGb());
                dayUsage += usageKb;
                categoryMap.merge(appCategoryCache.getCategoryName(a.appId()), usageKb, Long::sum);
            }

            dailyUsageList.add(DailyUsageItem.builder()
                    .date(d.date())
                    .day(date.getDayOfWeek().name())
                    .thisWeek(dayUsage)
                    .lastWeek(0L)
                    .build());

            if (isWeekend(date)) {
                totalWeekendUsage += dayUsage;
                weekendCount++;
            } else {
                totalWeekdayUsage += dayUsage;
                weekdayCount++;
            }
        }
        
        // 정렬: 날짜순
        dailyUsageList.sort(Comparator.comparing(DailyUsageItem::date));

        // 3. 요약 데이터 생성
        long lateNightUsage = 0;
        long studyTimeUsage = 0;
        List<HourlyUsageItem> hourlyUsageList = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            long usage = hourlyMap.getOrDefault(h, 0L);
            boolean isLateNight = h >= LATE_NIGHT_START && h <= LATE_NIGHT_END;
            boolean isStudyTime = h >= STUDY_TIME_START && h <= STUDY_TIME_END;
            
            if (isLateNight) lateNightUsage += usage;
            if (isStudyTime) studyTimeUsage += usage;

            hourlyUsageList.add(HourlyUsageItem.builder()
                    .hour(h)
                    .isLateNight(isLateNight)
                    .isStudyTime(isStudyTime)
                    .thisWeek(usage)
                    .lastWeek(0L)
                    .build());
        }

        SummaryData summaryData = SummaryData.builder()
                .dailySummary(DailySummaryItem.builder()
                        .weekdayAvg(weekdayCount > 0 ? totalWeekdayUsage / weekdayCount : 0)
                        .weekendAvg(weekendCount > 0 ? totalWeekendUsage / weekendCount : 0)
                        .build())
                .hourlySummary(HourlySummaryItem.builder()
                        .lateNightUsage(lateNightUsage)
                        .studyTimeUsage(studyTimeUsage)
                        .build())
                .build();

        // 4. 카테고리 상세 리스트 생성
        long totalCategoryUsage = 0;
        for (Long val : categoryMap.values()) totalCategoryUsage += val;

        List<CategoryUsageItem> thisWeekCategoryList = new ArrayList<>(categoryMap.size());
        for (Map.Entry<String, Long> e : categoryMap.entrySet()) {
            double percent = totalCategoryUsage > 0 ? Math.round((e.getValue() / (double) totalCategoryUsage) * 10000.0) / 100.0 : 0.0;
            thisWeekCategoryList.add(CategoryUsageItem.builder()
                    .category(e.getKey())
                    .usage(e.getValue())
                    .percent(percent)
                    .build());
        }
        thisWeekCategoryList.sort(Comparator.comparingLong(CategoryUsageItem::usage).reversed());

        UsageListData usageListData = UsageListData.builder()
                .totalUsage(totalUsage)
                .dailyUsageList(dailyUsageList)
                .hourlyUsageList(hourlyUsageList)
                .categoryUsageList(CategoryUsageListContainer.builder()
                        .thisWeek(thisWeekCategoryList)
                        .lastWeek(new ArrayList<>())
                        .comparison(new ArrayList<>())
                        .build())
                .build();

        return UsageAggregationResult.builder()
                .totalUsage(totalUsage)
                .summaryData(summaryData)
                .usageListData(usageListData)
                .build();
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
