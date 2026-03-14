package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.*;
import org.springframework.stereotype.Service;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.ComparisonCalculationService;
import lombok.RequiredArgsConstructor;

/**
 * 이번 주 집계 데이터와 지난주 리포트 스냅샷을 비교하여 최종 서빙용 지표를 산출하는 서비스
 */
@Service
@RequiredArgsConstructor
public class ComparisonCalculationServiceImpl implements ComparisonCalculationService {

    @Override
    public UsageComparisonResult calculate(UsageMetricsAggregationInput input, UsageAggregationResult thisWeek) {
        WeeklyReportSnapshot lastWeekSnapshot = input.lastWeekReport();
        
        // 1. 요약 데이터(SummaryData) 비교 및 고도화
        SummaryData finalSummary = calculateSummaryComparison(thisWeek.summaryData(), lastWeekSnapshot);

        // 2. 상세 리스트(UsageListData) 비교 및 고도화 (lastWeek 수치 주입)
        UsageListData finalList = calculateListComparison(thisWeek.usageListData(), lastWeekSnapshot);

        return UsageComparisonResult.builder()
            .kpi(calculateKpi(thisWeek.totalUsage(), 0, lastWeekSnapshot)) // TODO: 실제 점수(score) 연결
            .summaryData(finalSummary)
            .usageListData(finalList)
            .build();
    }

    /**
     * 평일/주말 평균 및 특정 시간대 사용량의 차이와 증감률을 계산함
     */
    private SummaryData calculateSummaryComparison(SummaryData thisWeek, WeeklyReportSnapshot lastWeek) {
        if (lastWeek == null) return thisWeek;

        DailySummaryItem dThis = thisWeek.dailySummary();
        DailySummaryItem dLast = lastWeek.summaryData().dailySummary();

        DailySummaryItem daily = DailySummaryItem.builder()
            .weekdayAvg(dThis.weekdayAvg())
            .weekdayAvgDiff(dThis.weekdayAvg() - dLast.weekdayAvg())
            .weekdayAvgChangeRate(calculateRate(dThis.weekdayAvg(), dLast.weekdayAvg()))
            .weekendAvg(dThis.weekendAvg())
            .weekendAvgDiff(dThis.weekendAvg() - dLast.weekendAvg())
            .weekendAvgChangeRate(calculateRate(dThis.weekendAvg(), dLast.weekendAvg()))
            .build();

        HourlySummaryItem hThis = thisWeek.hourlySummary();
        HourlySummaryItem hLast = lastWeek.summaryData().hourlySummary();

        HourlySummaryItem hourly = HourlySummaryItem.builder()
            .lateNightUsage(hThis.lateNightUsage())
            .lateNightUsageDiff(hThis.lateNightUsage() - hLast.lateNightUsage())
            .lateNightUsageChangeRate(calculateRate(hThis.lateNightUsage(), hLast.lateNightUsage()))
            .studyTimeUsage(hThis.studyTimeUsage())
            .studyTimeUsageDiff(hThis.studyTimeUsage() - hLast.studyTimeUsage())
            .studyTimeUsageChangeRate(calculateRate(hThis.studyTimeUsage(), hLast.studyTimeUsage()))
            .build();

        return SummaryData.builder()
            .dailySummary(daily)
            .hourlySummary(hourly)
            .categorySummary(thisWeek.categorySummary()) // 카테고리 요약은 원본 유지
            .build();
    }

    /**
     * 상세 차트용 리스트들에 지난주 데이터를 매칭하여 주입함
     */
    private UsageListData calculateListComparison(UsageListData thisWeek, WeeklyReportSnapshot lastWeek) {
        if (lastWeek == null) return thisWeek;

        // 1. 일별 데이터 매칭 (요일 기준) - unified DTO 사용
        Map<String, Long> lastDailyMap = lastWeek.usageListData().dailyUsageList().stream()
            .collect(Collectors.toMap(DailyUsageItem::day, DailyUsageItem::thisWeek, (existing, replacement) -> existing));

        List<DailyUsageItem> dailyList = thisWeek.dailyUsageList().stream()
            .map(item -> DailyUsageItem.builder()
                .date(item.date())
                .day(item.day())
                .thisWeek(item.thisWeek())
                .lastWeek(lastDailyMap.getOrDefault(item.day(), 0L))
                .build())
            .toList();

        // 2. 시간대별 데이터 매칭 (시간 기준)
        Map<Integer, Long> lastHourlyMap = lastWeek.usageListData().hourlyUsageList().stream()
            .collect(Collectors.toMap(HourlyUsageItem::hour, HourlyUsageItem::thisWeek, (existing, replacement) -> existing));

        List<HourlyUsageItem> hourlyList = thisWeek.hourlyUsageList().stream()
            .map(item -> HourlyUsageItem.builder()
                .hour(item.hour())
                .isLateNight(item.isLateNight())
                .isStudyTime(item.isStudyTime())
                .thisWeek(item.thisWeek())
                .lastWeek(lastHourlyMap.getOrDefault(item.hour(), 0L))
                .build())
            .toList();

        // 3. 카테고리별 데이터 매칭 및 증감률 계산
        Map<String, Long> lastCategoryMap = lastWeek.usageListData().categoryUsageList().stream()
            .collect(Collectors.toMap(CategoryUsageItem::category, CategoryUsageItem::thisWeek, (existing, replacement) -> existing));

        List<CategoryUsageItem> categoryList = thisWeek.categoryUsageList().stream()
            .map(item -> {
                long lastVal = lastCategoryMap.getOrDefault(item.category(), 0L);
                return CategoryUsageItem.builder()
                    .category(item.category())
                    .thisWeek(item.thisWeek())
                    .lastWeek(lastVal)
                    .changeRate(calculateRate(item.thisWeek(), lastVal))
                    .build();
            })
            .toList();

        return UsageListData.builder()
            .totalUsage(thisWeek.totalUsage())
            .dailyUsageList(dailyList)
            .hourlyUsageList(hourlyList)
            .categoryUsageList(categoryList)
            .build();
    }

    private KpiComparison calculateKpi(long thisWeekTotal, int thisWeekScore, WeeklyReportSnapshot lastWeek) {
        if (lastWeek == null) {
            return KpiComparison.builder()
                .totalUsage(ComparisonValue.builder().diff(thisWeekTotal).changeRatePct(0.0).build())
                .scoreDiff(0)
                .build();
        }
        long diff = thisWeekTotal - lastWeek.totalUsage();
        double rate = calculateRate(thisWeekTotal, lastWeek.totalUsage());
        return KpiComparison.builder()
            .totalUsage(new ComparisonValue(diff, rate))
            .scoreDiff(thisWeekScore - lastWeek.totalScore())
            .build();
    }

    private double calculateRate(long current, long previous) {
        if (previous <= 0) return 0.0;
        return round(((double) (current - previous) / previous) * 100.0);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
