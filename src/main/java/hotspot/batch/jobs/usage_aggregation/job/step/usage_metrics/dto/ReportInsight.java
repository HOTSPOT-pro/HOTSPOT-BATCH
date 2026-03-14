package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;
import lombok.Builder;

/**
 * 분석 데이터를 바탕으로 도출된 비즈니스 인사이트 (점수, 태그)
 */
@Builder
public record ReportInsight(
    ScoreResult scoreResult,
    List<String> tags
) {}
