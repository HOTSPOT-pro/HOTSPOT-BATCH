package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;
import lombok.Builder;

/**
 * 카테고리별 통계 데이터의 최종 컨테이너
 * 프론트엔드 요구사항: 이번 주, 지난 주, 비교 리스트를 그룹화하여 한 번에 제공함
 */
@Builder
public record CategoryUsageListContainer(
    List<CategoryUsageItem> thisWeek,     // 이번 주 카테고리별 사용량 및 비중 리스트
    List<CategoryUsageItem> lastWeek,     // 지난주 카테고리별 사용량 및 비중 리스트
    List<CategoryComparisonItem> comparison // 전주 대비 카테고리별 증감률 리스트
) {}
