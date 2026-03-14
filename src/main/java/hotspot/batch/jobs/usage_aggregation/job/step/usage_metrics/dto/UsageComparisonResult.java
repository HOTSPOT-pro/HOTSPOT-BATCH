package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;
import lombok.Builder;

/**
 * 이번 주와 지난주 사용량을 비교한 최종 통계 결과물
 * Processor에서 생성되어 최종 WeeklyReport 엔티티에 매핑됨
 */
@Builder
public record UsageComparisonResult(
    KpiComparison kpi,
    DailyComparison daily,
    TimePatternComparison timePattern,
    List<CategoryDiff> categoryDiffs
) {}
