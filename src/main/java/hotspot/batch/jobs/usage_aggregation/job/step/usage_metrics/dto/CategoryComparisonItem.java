package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 전주 대비 카테고리별 증감률 정보
 */
@Builder
public record CategoryComparisonItem(
    String category,  // 카테고리명 (STUDY, MEDIA, GAME 등)
    double changeRate // 전주 대비 증감률 (%)
) {}
