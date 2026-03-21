package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 리포트의 요약 통계 데이터 묶음
 * 중복된 카테고리 정보 제거
 */
@Builder
public record SummaryData(
    DailySummaryItem dailySummary,
    HourlySummaryItem hourlySummary
) {}
