package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;

/**
 * WeeklyReport JSONB 컬럼에 저장될 최종 결과물 스냅샷
 */
public record WeeklyReportSnapshot(
    Summary summary,
    List<UsageItem> usageList,
    List<String> tags,
    int totalScore,
    String scoreLevel,
    Comparison comparison // 지난주 대비 증감 정보
) {}

/**
 * 리포트 통계 정보
 */
record Summary(
    long totalUsage,
    long averageUsage,
    String peakDay,
    int peakTime
) {}

/**
 * 리포트 개별 지표 정보
 */
record UsageItem(
    String date,
    long usage,
    double changeRate // 전일 대비
) {}

/**
 * 지난주 데이터와의 비교 정보
 */
record Comparison(
    long lastWeekTotalUsage,
    double totalChangeRate
) {}
