package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 카테고리별 합산 및 비교 사용량 정보 (차트 데이터용)
 */
@Builder
public record CategoryUsageItem(
    String category, // 카테고리명 (STUDY, MEDIA, GAME 등)
    long thisWeek,   // 이번 주 사용량 (KB)
    long lastWeek,   // 지난주 사용량 (KB)
    double changeRate // 전주 대비 증감률 (%)
) {}
