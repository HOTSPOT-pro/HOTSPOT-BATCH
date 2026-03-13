package hotspot.batch.jobs.usage_aggregation.job;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 리포트 분석 결과에 따라 부여되는 인사이트 태그 Enum
 * 1~4순위: 주의/경고형 태그
 * 5~6순위: 패턴/변화형 태그
 * 7~8순위: 긍정/일반형 태그
 */
public enum ReportTag {
    LATE_NIGHT_HIGH(1, "심야 사용 높음"),
    USAGE_SPIKE(2, "사용량 급증"),
    ENTERTAINMENT_HIGH(3, "여가 비중 높음"),
    STUDY_LOW(4, "학습 비중 낮음"),
    WEEKEND_BURST(5, "주말 몰입"),
    STUDY_FOCUS_UP(6, "학습 집중도 향상"),
    LOW_USAGE(7, "낮은 사용량"),
    BALANCED_GOOD(8, "균형 잡힌 사용");

    private final int priority;
    private final String description;

    ReportTag(int priority, String description) {
        this.priority = priority;
        this.description = description;
    }

    public int getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 우선순위 순서대로 정렬된 태그 리스트를 반환함
     */
    public static List<ReportTag> valuesInPriorityOrder() {
        return Arrays.stream(values())
                .sorted(Comparator.comparingInt(ReportTag::getPriority))
                .collect(Collectors.toList());
    }
}
