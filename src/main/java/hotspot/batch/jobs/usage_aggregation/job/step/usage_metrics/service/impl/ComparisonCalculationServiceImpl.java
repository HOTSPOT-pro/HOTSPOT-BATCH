package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.CategoryDiff;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ComparisonValue;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.DailyComparison;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.KpiComparison;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.TimePatternComparison;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageComparisonResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReportSnapshot;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.ComparisonCalculationService;
import lombok.RequiredArgsConstructor;

/**
 * 이번 주 집계 데이터와 지난주 리포트 스냅샷을 비교하여 증감 지표를 산출하는 서비스 구현체
 */
@Service
@RequiredArgsConstructor
public class ComparisonCalculationServiceImpl implements ComparisonCalculationService {

    /**
     * 이번 주와 지난주의 사용량 데이터를 비교하여 최종 결과를 도출함
     * 1. 전달받은 이번 주 요약 데이터(thisWeekSummary)와 입력 객체(input)의 Raw 데이터 사용
     * 2. 지난주 스냅샷(lastWeekReport) 존재 여부에 따른 분기 처리
     * 3. KPI, 일별, 시간대별, 카테고리별 증감 수치 연산
     */
    @Override
    public UsageComparisonResult calculate(UsageMetricsAggregationInput input, SummaryData thisWeekSummary) {
        // 1. 이번 주 총 사용량 합산 (Raw 데이터 기반)
        long thisWeekTotalUsage = input.weeklyHourlyUsage().stream()
            .flatMap(d -> d.hourlyUsage().values().stream())
            .mapToLong(Long::longValue)
            .sum();

        WeeklyReportSnapshot lastWeek = input.lastWeekReport();

        // 2. 지난주 데이터가 없는 경우 (신규 유저) 기본값 반환
        if (lastWeek == null) {
            return createDefaultResult(thisWeekTotalUsage, thisWeekSummary);
        }

        // 3. 지표별 비교 연산 수행
        return UsageComparisonResult.builder()
            .kpi(calculateKpi(thisWeekTotalUsage, 0, lastWeek)) // TODO: 실제 점수(score) 계산 로직 연결 시 0 교체
            .daily(calculateDaily(thisWeekSummary, lastWeek))
            .timePattern(calculateTimePattern(thisWeekSummary, lastWeek))
            .categoryDiffs(calculateCategoryDiffs(thisWeekSummary, thisWeekTotalUsage, lastWeek))
            .build();
    }

    /**
     * 전체 사용량(Usage)과 리포트 점수(Score)의 차이를 계산함
     */
    private KpiComparison calculateKpi(long thisWeekTotal, int thisWeekScore, WeeklyReportSnapshot lastWeek) {
        return KpiComparison.builder()
            .totalUsage(calculateComparison(thisWeekTotal, lastWeek.totalUsage()))
            .scoreDiff(thisWeekScore - lastWeek.totalScore())
            .build();
    }

    /**
     * 평일 및 주말 평균 사용량의 증감을 계산함
     */
    private DailyComparison calculateDaily(SummaryData thisWeek, WeeklyReportSnapshot lastWeek) {
        SummaryData lastWeekSummary = lastWeek.summaryData();
        return DailyComparison.builder()
            .weekdayAvg(calculateComparison(thisWeek.dailySummary().weekdayAvg(), lastWeekSummary.dailySummary().weekdayAvg()))
            .weekendAvg(calculateComparison(thisWeek.dailySummary().weekendAvg(), lastWeekSummary.dailySummary().weekendAvg()))
            .build();
    }

    /**
     * 심야 시간대와 학습 시간대의 사용 패턴 증감을 계산함
     */
    private TimePatternComparison calculateTimePattern(SummaryData thisWeek, WeeklyReportSnapshot lastWeek) {
        SummaryData lastWeekSummary = lastWeek.summaryData();
        return TimePatternComparison.builder()
            .lateNight(calculateComparison(thisWeek.hourlySummary().lateNightUsage(), lastWeekSummary.hourlySummary().lateNightUsage()))
            .studyTime(calculateComparison(thisWeek.hourlySummary().studyTimeUsage(), lastWeekSummary.hourlySummary().studyTimeUsage()))
            .build();
    }

    /**
     * 각 앱 카테고리별 사용량 비중(Share)의 변화를 계산함
     */
    private List<CategoryDiff> calculateCategoryDiffs(SummaryData thisWeek, long thisWeekTotal, WeeklyReportSnapshot lastWeek) {
        Map<String, Double> thisWeekShares = thisWeek.categorySummary().stream()
            .collect(Collectors.toMap(c -> c.category(), c -> c.percent()));

        Map<String, Double> lastWeekShares = lastWeek.summaryData().categorySummary().stream()
            .collect(Collectors.toMap(c -> c.category(), c -> c.percent()));

        List<CategoryDiff> diffs = new ArrayList<>();
        thisWeekShares.forEach((category, share) -> {
            double lastShare = lastWeekShares.getOrDefault(category, 0.0);
            diffs.add(CategoryDiff.builder()
                .category(category)
                .usage(ComparisonValue.zero()) // 상세 사용량은 추후 SummaryData 구조 개선 시 보완 가능
                .shareDiffPct(round(share - lastShare))
                .build());
        });
        
        return diffs;
    }

    /**
     * 기준값(previous) 대비 현재값(current)의 차이(diff)와 증감률(%)을 계산하는 유틸리티 메서드
     * Divide by Zero 방지 로직 포함
     */
    private ComparisonValue calculateComparison(long current, long previous) {
        long diff = current - previous;
        double rate = 0.0;
        if (previous > 0) {
            rate = round(((double) diff / previous) * 100);
        }
        return new ComparisonValue(diff, rate);
    }

    /**
     * 수치 데이터의 일관성을 위해 소수점 둘째 자리까지 반올림함
     */
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 지난주 데이터가 없는 신규 유저를 위해 빈 결과 객체를 생성함
     */
    private UsageComparisonResult createDefaultResult(long totalUsage, SummaryData summary) {
        return UsageComparisonResult.builder()
            .kpi(new KpiComparison(new ComparisonValue(totalUsage, 0.0), 0))
            .daily(new DailyComparison(ComparisonValue.zero(), ComparisonValue.zero()))
            .timePattern(new TimePatternComparison(ComparisonValue.zero(), ComparisonValue.zero()))
            .categoryDiffs(new ArrayList<>())
            .build();
    }
}
