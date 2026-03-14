package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 앱 카테고리별 사용량 증감 및 점유율 변화 데이터
 */
@Builder
public record CategoryDiff(
    String category,
    ComparisonValue usage,
    /**
     * 지난주 대비 해당 카테고리가 전체 사용량에서 차지하는 비중(점유율)의 차이 (%p)
     * 예: 지난주 점유율 20%, 이번 주 점유율 25% 이면 shareDiffPct는 +5.0
     */
    double shareDiffPct
) {}
