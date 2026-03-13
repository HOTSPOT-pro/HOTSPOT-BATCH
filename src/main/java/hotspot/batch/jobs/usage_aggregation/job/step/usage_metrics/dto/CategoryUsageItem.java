package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 카테고리별 합산 사용량 정보 (차트 데이터용)
 */
@Builder
public record CategoryUsageItem(
    String category, // 카테고리명 (STUDY, MEDIA, GAME 등)
    long usage       // 해당 카테고리의 총 사용량 (KB)
) {}
