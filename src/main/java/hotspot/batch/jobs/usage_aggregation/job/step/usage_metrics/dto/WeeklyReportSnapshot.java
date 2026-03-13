package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * DB에서 읽어오는 리포트의 최종 스냅샷 정보
 * 이번 주 지표와의 비교 및 점수 계산의 기준점으로 사용됨
 */
@Builder
public record WeeklyReportSnapshot(
    long totalUsage,
    int totalScore,
    SummaryData summaryData,
    LastWeekUsageListData usageListData
) {}
