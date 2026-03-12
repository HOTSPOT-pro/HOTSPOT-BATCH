package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 카테고리별 사용량 비율 정보
 */
@Builder
public record CategorySummaryItem(
    String category,
    double percent
) {}
