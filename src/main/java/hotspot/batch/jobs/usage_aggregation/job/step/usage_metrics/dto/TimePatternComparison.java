package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import lombok.Builder;

/**
 * 특수 시간대(심야, 학습시간 등) 사용량 패턴 비교 데이터
 */
@Builder
public record TimePatternComparison(
    ComparisonValue lateNight,
    ComparisonValue studyTime
) {}
