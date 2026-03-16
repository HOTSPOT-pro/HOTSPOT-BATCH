package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import hotspot.batch.jobs.usage_aggregation.job.ReportTag;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.CategorySummaryItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ReportInsight;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ScoreReason;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ScoreData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageAggregationResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageComparisonResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReportSnapshot;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.ReportInsightService;
import lombok.RequiredArgsConstructor;

/**
 * 인사이트 분석 계층: 가공된 데이터를 바탕으로 비즈니스 로직(태그, 점수)을 적용하여 통찰을 도출함
 */
@Service
@RequiredArgsConstructor
public class ReportInsightServiceImpl implements ReportInsightService {

    // 태그/점수 판정 공통 상수
    private static final List<String> ENTERTAINMENT_CATEGORIES = List.of("MEDIA", "TOON", "SNS", "GAME");
    private static final String STUDY_CATEGORY = "STUDY";

    // 태그 임계치
    private static final long LATE_NIGHT_USAGE_MIN = 120L * 60L * 1000L;
    private static final double LATE_NIGHT_RATIO_MIN = 20.0;
    private static final double USAGE_SPIKE_THRESHOLD = 30.0;
    private static final double ENTERTAINMENT_RATIO_MAX = 60.0;
    private static final double STUDY_RATIO_MIN = 10.0;
    private static final double WEEKEND_BURST_MULTIPLIER = 2.0;
    private static final double STUDY_FOCUS_UP_THRESHOLD = 5.0;
    private static final long LOW_USAGE_THRESHOLD = 300L * 60L * 1000L;

    // 점수 임계치 및 기준
    private static final int BASE_SCORE = 80;
    private static final long USAGE_MODERATE = 1200L * 60L * 1000L;
    private static final long USAGE_EXCESSIVE = 3000L * 60L * 1000L;

    /**
     * 집계된 수치와 전주 대비 변화를 분석하여 통합 인사이트(태그, 점수)를 반환함
     */
    @Override
    public ReportInsight analyze(UsageAggregationResult agg, UsageComparisonResult comparison, WeeklyReportSnapshot lastWeek) {
        SummaryData summary = agg.summaryData();
        long totalUsage = agg.totalUsage();

        // 1. 인사이트 태그 생성
        List<ReportTag> tagList = generateTags(summary, comparison, totalUsage);

        // 2. 리포트 점수 계산
        ScoreData scoreData = calculateScore(summary, comparison, totalUsage, lastWeek);

        return ReportInsight.builder()
                .scoreData(scoreData)
                .tags(tagList.stream().map(ReportTag::name).toList())
                .build();
    }

    /**
     * 우선순위 규칙에 따라 매칭되는 태그를 최대 2개까지 수집함
     */
    private List<ReportTag> generateTags(SummaryData summary, UsageComparisonResult comparison, long totalUsage) {
        List<ReportTag> selected = new ArrayList<>();
        for (ReportTag tag : ReportTag.valuesInPriorityOrder()) {
            if (isTagConditionMet(tag, summary, comparison, totalUsage)) selected.add(tag);
            if (selected.size() >= 2) break;
        }
        if (selected.isEmpty()) selected.add(ReportTag.BALANCED_GOOD);
        return selected;
    }

    /**
     * 각 태그별 비즈니스 판정 로직을 실행함
     */
    private boolean isTagConditionMet(ReportTag tag, SummaryData summary, UsageComparisonResult comparison, long totalUsage) {
        return switch (tag) {
            case LATE_NIGHT_HIGH -> (summary.hourlySummary().lateNightUsage() >= LATE_NIGHT_USAGE_MIN && 
                                    (totalUsage > 0 && (summary.hourlySummary().lateNightUsage() / (double) totalUsage * 100) >= LATE_NIGHT_RATIO_MIN));
            case USAGE_SPIKE -> comparison.kpi().totalUsage().changeRatePct() >= USAGE_SPIKE_THRESHOLD;
            case ENTERTAINMENT_HIGH -> getCategoryRatio(summary, ENTERTAINMENT_CATEGORIES) >= ENTERTAINMENT_RATIO_MAX;
            case STUDY_LOW -> getCategoryRatio(summary, List.of(STUDY_CATEGORY)) <= STUDY_RATIO_MIN;
            case WEEKEND_BURST -> summary.dailySummary().weekdayAvg() > 0 && (summary.dailySummary().weekendAvg() / (double) summary.dailySummary().weekdayAvg() >= WEEKEND_BURST_MULTIPLIER);
            case STUDY_FOCUS_UP -> comparison.usageListData().categoryUsageList().stream()
                    .anyMatch(c -> STUDY_CATEGORY.equalsIgnoreCase(c.category()) && c.changeRate() >= STUDY_FOCUS_UP_THRESHOLD);
            case LOW_USAGE -> totalUsage > 0 && totalUsage < LOW_USAGE_THRESHOLD;
            case BALANCED_GOOD -> false;
        };
    }

    /**
     * 5대 핵심 지표(사용량, 비중, 심야, 패턴, 개선도)를 정밀 분석하여 점수와 사유를 산출함
     */
    private ScoreData calculateScore(SummaryData summary, UsageComparisonResult comparison, long totalUsage, WeeklyReportSnapshot lastWeek) {
        List<ScoreReason> reasons = new ArrayList<>();
        int score = BASE_SCORE;

        // 사용량 절제 (30점)
        if (totalUsage < USAGE_MODERATE) { score += 10; reasons.add(new ScoreReason(10, "절제된 사용 습관")); }
        else if (totalUsage > USAGE_EXCESSIVE) { score -= 20; reasons.add(new ScoreReason(-20, "과도한 사용량 경고")); }

        // 생산성 (25점)
        double study = getCategoryRatio(summary, List.of(STUDY_CATEGORY));
        double leisure = getCategoryRatio(summary, ENTERTAINMENT_CATEGORIES);
        if (study > leisure && study >= 30.0) { score += 15; reasons.add(new ScoreReason(15, "높은 생산성 비중")); }
        else if (leisure >= 60.0) { score -= 10; reasons.add(new ScoreReason(-10, "여가 활동 편중")); }

        // 수면 건강 (20점)
        double sleepRatio = (totalUsage > 0) ? (summary.hourlySummary().lateNightUsage() / (double) totalUsage * 100) : 0;
        if (sleepRatio < 5.0) { score += 5; reasons.add(new ScoreReason(5, "안정적인 수면 위생")); }
        else if (sleepRatio > 25.0) { score -= 20; reasons.add(new ScoreReason(-20, "심각한 심야 과사용")); }

        int finalScore = Math.max(0, Math.min(100, score));

        // 전주 대비 점수 차이 계산
        int lastScore = (lastWeek != null && lastWeek.scoreData() != null) ? lastWeek.scoreData().totalScore() : finalScore;
        int scoreDiff = finalScore - lastScore;

        return ScoreData.builder()
                .totalScore(finalScore)
                .scoreDiff(scoreDiff)
                .scoreLevel(determineLevel(finalScore))
                .reasons(reasons)
                .build();
    }

    /**
     * 특정 카테고리 그룹이 전체 사용량에서 차지하는 합산 비중을 구함
     */
    private double getCategoryRatio(SummaryData summary, List<String> categories) {
        return summary.categorySummary().stream()
                .filter(c -> categories.contains(c.category().toUpperCase()))
                .mapToDouble(CategorySummaryItem::percent).sum();
    }

    /**
     * 최종 점수에 따른 리포트 등급을 결정함
     */
    private String determineLevel(int score) {
        if (score >= 90) return "EXCELLENT";
        if (score >= 80) return "GOOD";
        if (score >= 60) return "NORMAL";
        return "WARNING";
    }
}
