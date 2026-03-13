package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.CategorySummaryItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ScoreReason;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ScoreResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageComparisonResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.ScoreCalculationService;
import lombok.RequiredArgsConstructor;

/**
 * 5대 지표(사용량, 비중, 심야, 패턴, 개선도)를 정밀 분석하여 리포트 점수를 산출하는 서비스 구현체
 */
@Service
@RequiredArgsConstructor
public class ScoreCalculationServiceImpl implements ScoreCalculationService {

    // 점수 기본값 및 한계치
    private static final int BASE_SCORE = 80;
    private static final int MAX_SCORE = 100;
    private static final int MIN_SCORE = 0;

    // ① 사용량 절제 지표 (30점) - 1,200분 (20시간) / 2,100분 (35시간) / 3,000분 (50시간) 기준 (분 단위 가정)
    private static final long USAGE_MODERATE = 1200L * 60L * 1000L;
    private static final long USAGE_HIGH = 2100L * 60L * 1000L;
    private static final long USAGE_EXCESSIVE = 3000L * 60L * 1000L;

    // ② 생산성 비중 지표 (25점) - 학습 대 여가 비율 기준
    private static final List<String> ENTERTAINMENT_CATEGORIES = List.of("MEDIA", "TOON", "SNS", "GAME");
    private static final String STUDY_CATEGORY = "STUDY";

    // ③ 수면 건강 지표 (20점) - 심야 비중 (%) 기준
    private static final double SLEEP_EXCELLENT_RATIO = 5.0;
    private static final double SLEEP_WARNING_RATIO = 15.0;
    private static final double SLEEP_DANGER_RATIO = 25.0;

    // ④ 생활 패턴 안정성 (15점) - 주말/평일 비율 기준
    private static final double PATTERN_STABLE_RATIO = 1.3;
    private static final double PATTERN_UNSTABLE_RATIO = 2.0;

    // ⑤ 자기 개선 지표 (10점) - 전주 대비 증감률 (%) 기준
    private static final double IMPROVEMENT_THRESHOLD = 10.0;

    /**
     * 5대 핵심 지표를 바탕으로 리포트 최종 점수를 산출함
     * 80점 기본 점수에서 시작하여 각 지표별로 가점 및 감점을 적용함
     */
    @Override
    public ScoreResult calculate(SummaryData summary, UsageComparisonResult comparison, long totalUsage) {
        List<ScoreReason> reasons = new ArrayList<>();
        int currentScore = BASE_SCORE;

        // 1. 사용량 절제 지표 분석 (30점)
        currentScore += evaluateUsageVolume(totalUsage, reasons);

        // 2. 생산성 비중 지표 분석 (25점)
        currentScore += evaluateProductivity(summary, reasons);

        // 3. 수면 건강 지표 분석 (20점)
        currentScore += evaluateSleepHygiene(summary, totalUsage, reasons);

        // 4. 생활 패턴 안정성 분석 (15점)
        currentScore += evaluatePatternConsistency(summary, reasons);

        // 5. 자기 개선 지표 분석 (10점)
        currentScore += evaluateImprovement(comparison, reasons);

        // 최종 점수 보정 (0~100)
        int finalScore = Math.max(MIN_SCORE, Math.min(MAX_SCORE, currentScore));
        
        return ScoreResult.builder()
                .totalScore(finalScore)
                .scoreLevel(determineLevel(finalScore))
                .reasons(reasons)
                .build();
    }

    /**
     * 주간 총 사용량을 기준으로 점수를 가감함 (최대 -20점)
     * 절대적인 스마트폰 사용 시간이 건강한 수준인지 판단함
     */
    private int evaluateUsageVolume(long totalUsage, List<ScoreReason> reasons) {
        if (totalUsage < USAGE_MODERATE) {
            reasons.add(new ScoreReason(10, "매우 절제된 사용 습관"));
            return 10;
        } else if (totalUsage > USAGE_EXCESSIVE) {
            reasons.add(new ScoreReason(-20, "과도한 사용량 경고"));
            return -20;
        } else if (totalUsage > USAGE_HIGH) {
            reasons.add(new ScoreReason(-10, "사용량 주의가 필요함"));
            return -10;
        }
        return 0;
    }

    /**
     * 학습 카테고리와 여가(미디어/게임/SNS) 카테고리의 비중을 비교하여 점수를 가감함 (최대 +15점, -10점)
     * 사용자의 시간 배분 효율성을 판단함
     */
    private int evaluateProductivity(SummaryData summary, List<ScoreReason> reasons) {
        double studyPercent = summary.categorySummary().stream()
                .filter(c -> STUDY_CATEGORY.equalsIgnoreCase(c.category()))
                .mapToDouble(CategorySummaryItem::percent)
                .findFirst().orElse(0.0);

        double leisurePercent = summary.categorySummary().stream()
                .filter(c -> ENTERTAINMENT_CATEGORIES.contains(c.category().toUpperCase()))
                .mapToDouble(CategorySummaryItem::percent)
                .sum();

        if (studyPercent > leisurePercent && studyPercent >= 30.0) {
            reasons.add(new ScoreReason(15, "생산적인 활동 비중 매우 높음"));
            return 15;
        } else if (studyPercent >= 20.0) {
            reasons.add(new ScoreReason(5, "균형 잡힌 학습 비중 유지"));
            return 5;
        } else if (leisurePercent >= 60.0) {
            reasons.add(new ScoreReason(-10, "여가 활동에 편중된 사용"));
            return -10;
        }
        return 0;
    }

    /**
     * 심야 시간대(00~06시)의 사용 비중을 분석하여 점수를 가감함 (최대 +5점, -20점)
     * 수면 건강과 생활 리듬을 판단함
     */
    private int evaluateSleepHygiene(SummaryData summary, long totalUsage, List<ScoreReason> reasons) {
        long lateNightUsage = summary.hourlySummary().lateNightUsage();
        double ratio = (totalUsage > 0) ? (lateNightUsage / (double) totalUsage) * 100.0 : 0.0;

        if (ratio < SLEEP_EXCELLENT_RATIO) {
            reasons.add(new ScoreReason(5, "훌륭한 수면 위생 유지"));
            return 5;
        } else if (ratio > SLEEP_DANGER_RATIO) {
            reasons.add(new ScoreReason(-20, "심각한 심야 시간대 과사용"));
            return -20;
        } else if (ratio > SLEEP_WARNING_RATIO) {
            reasons.add(new ScoreReason(-10, "심야 시간대 사용 비중 높음"));
            return -10;
        }
        return 0;
    }

    /**
     * 주말 사용량과 평일 사용량의 편차를 분석하여 점수를 가감함 (최대 +5점, -5점)
     * 규칙적인 기기 사용 패턴을 유지하고 있는지 판단함
     */
    private int evaluatePatternConsistency(SummaryData summary, List<ScoreReason> reasons) {
        long weekdayAvg = summary.dailySummary().weekdayAvg();
        long weekendAvg = summary.dailySummary().weekendAvg();
        double ratio = (weekdayAvg > 0) ? (weekendAvg / (double) weekdayAvg) : 1.0;

        if (ratio <= PATTERN_STABLE_RATIO) {
            reasons.add(new ScoreReason(5, "안정적인 일주일 생활 패턴"));
            return 5;
        } else if (ratio >= PATTERN_UNSTABLE_RATIO) {
            reasons.add(new ScoreReason(-5, "주말 몰아 쓰기 패턴 주의"));
            return -5;
        }
        return 0;
    }

    /**
     * 지난주 대비 총 사용량의 증감을 분석하여 보너스 또는 패널티를 부여함 (최대 +10점, -10점)
     * 사용자 스스로 사용량을 조절하려는 노력(개선도)을 판단함
     */
    private int evaluateImprovement(UsageComparisonResult comparison, List<ScoreReason> reasons) {
        double growthRate = comparison.kpi().totalUsage().changeRatePct();

        if (growthRate <= -IMPROVEMENT_THRESHOLD) {
            reasons.add(new ScoreReason(10, "지난주보다 사용량 대폭 감소"));
            return 10;
        } else if (growthRate >= IMPROVEMENT_THRESHOLD) {
            reasons.add(new ScoreReason(-10, "지난주보다 사용량 급증"));
            return -10;
        }
        return 0;
    }

    private String determineLevel(int score) {
        if (score >= 90) return "EXCELLENT";
        if (score >= 80) return "GOOD";
        if (score >= 60) return "NORMAL";
        return "WARNING";
    }
}
