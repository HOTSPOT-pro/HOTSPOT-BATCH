package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import hotspot.batch.jobs.usage_aggregation.job.ReportTag;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.CategorySummaryItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageComparisonResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.TagGenerationService;
import lombok.RequiredArgsConstructor;

/**
 * 통계 데이터를 분석하여 사용자 맞춤형 인사이트 태그를 생성하는 서비스 구현체
 */
@Service
@RequiredArgsConstructor
public class TagGenerationServiceImpl implements TagGenerationService {

    // 판정 임계치 상수 정의
    private static final long LATE_NIGHT_USAGE_THRESHOLD = 120L * 60L * 1000L; // 예: 120분 (밀리초/또는 프로젝트 기준 단위)
    private static final double LATE_NIGHT_RATIO_THRESHOLD = 20.0; // 전체 대비 20%
    private static final double USAGE_SPIKE_THRESHOLD = 30.0; // 전주 대비 30% 증가
    private static final double ENTERTAINMENT_RATIO_THRESHOLD = 60.0; // 여가 비중 60% 이상
    private static final double STUDY_RATIO_LOW_THRESHOLD = 10.0; // 학습 비중 10% 이하
    private static final double WEEKEND_BURST_MULTIPLIER = 2.0; // 주말이 평일의 2배 이상
    private static final double STUDY_FOCUS_UP_THRESHOLD = 5.0; // 학습 점유율 5%p 이상 상승
    private static final long LOW_USAGE_THRESHOLD = 300L * 60L * 1000L; // 예: 주간 총 300분 미만

    // 여가성 카테고리 분류 정의 (시스템 DB 코드 기준)
    private static final List<String> ENTERTAINMENT_CATEGORIES = List.of("MEDIA", "TOON", "SNS", "GAME");
    private static final String STUDY_CATEGORY = "STUDY";

    @Override
    public List<ReportTag> generate(SummaryData summary, UsageComparisonResult comparison, long totalUsage) {
        List<ReportTag> selectedTags = new ArrayList<>();

        // 우선순위가 높은 태그부터 순회하며 조건 체크
        for (ReportTag tag : ReportTag.valuesInPriorityOrder()) {
            if (isConditionMet(tag, summary, comparison, totalUsage)) {
                selectedTags.add(tag);
            }
            // 최대 2개까지만 선택
            if (selectedTags.size() >= 2) {
                break;
            }
        }

        // 매칭된 태그가 하나도 없으면 기본 태그 부여
        if (selectedTags.isEmpty()) {
            selectedTags.add(ReportTag.BALANCED_GOOD);
        }

        return selectedTags;
    }

    /**
     * 각 태그별 판정 로직 분기
     */
    private boolean isConditionMet(ReportTag tag, SummaryData summary, UsageComparisonResult comparison, long totalUsage) {
        return switch (tag) {
            case LATE_NIGHT_HIGH -> checkLateNightHigh(summary, totalUsage);
            case USAGE_SPIKE -> checkUsageSpike(comparison);
            case ENTERTAINMENT_HIGH -> checkEntertainmentHigh(summary);
            case STUDY_LOW -> checkStudyLow(summary);
            case WEEKEND_BURST -> checkWeekendBurst(summary);
            case STUDY_FOCUS_UP -> checkStudyFocusUp(comparison);
            case LOW_USAGE -> checkLowUsage(totalUsage);
            case BALANCED_GOOD -> false; // 루프 마지막에 별도 처리
        };
    }

    /**
     * 심야 사용량 절대치가 기준을 넘고, 전체 사용량 중 비중이 20% 이상인 경우
     */
    private boolean checkLateNightHigh(SummaryData summary, long totalUsage) {
        long lateNightUsage = summary.hourlySummary().lateNightUsage();
        double lateNightRatio = (totalUsage > 0) ? (lateNightUsage / (double) totalUsage) * 100.0 : 0.0;
        return lateNightUsage >= LATE_NIGHT_USAGE_THRESHOLD && lateNightRatio >= LATE_NIGHT_RATIO_THRESHOLD;
    }

    /**
     * 전주 대비 총 사용량이 30% 이상 급증한 경우
     */
    private boolean checkUsageSpike(UsageComparisonResult comparison) {
        return comparison.kpi().totalUsage().changeRatePct() >= USAGE_SPIKE_THRESHOLD;
    }

    /**
     * 여가성 카테고리(미디어, 게임, SNS)의 합산 비중이 60% 이상인 경우
     */
    private boolean checkEntertainmentHigh(SummaryData summary) {
        double entertainRatio = summary.categorySummary().stream()
            .filter(c -> ENTERTAINMENT_CATEGORIES.contains(c.category().toLowerCase()))
            .mapToDouble(CategorySummaryItem::percent)
            .sum();
        return entertainRatio >= ENTERTAINMENT_RATIO_THRESHOLD;
    }

    /**
     * 학습 카테고리의 비중이 10% 이하인 경우
     */
    private boolean checkStudyLow(SummaryData summary) {
        double studyRatio = summary.categorySummary().stream()
            .filter(c -> STUDY_CATEGORY.equalsIgnoreCase(c.category()))
            .mapToDouble(CategorySummaryItem::percent)
            .sum();
        return studyRatio <= STUDY_RATIO_LOW_THRESHOLD;
    }

    /**
     * 주말 평균 사용량이 평일 평균의 2배 이상인 경우
     */
    private boolean checkWeekendBurst(SummaryData summary) {
        long weekdayAvg = summary.dailySummary().weekdayAvg();
        long weekendAvg = summary.dailySummary().weekendAvg();
        return weekdayAvg > 0 && (weekendAvg / (double) weekdayAvg) >= WEEKEND_BURST_MULTIPLIER;
    }

    /**
     * 학습 카테고리의 점유율(%p)이 지난주 대비 5%p 이상 상승한 경우 (긍정 지표)
     */
    private boolean checkStudyFocusUp(UsageComparisonResult comparison) {
        return comparison.categoryDiffs().stream()
            .filter(c -> STUDY_CATEGORY.equalsIgnoreCase(c.category()))
            .anyMatch(c -> c.shareDiffPct() >= STUDY_FOCUS_UP_THRESHOLD);
    }

    /**
     * 주간 총 사용량이 절대 기준치(예: 300분) 미만인 경우
     */
    private boolean checkLowUsage(long totalUsage) {
        return totalUsage > 0 && totalUsage < LOW_USAGE_THRESHOLD;
    }
}
