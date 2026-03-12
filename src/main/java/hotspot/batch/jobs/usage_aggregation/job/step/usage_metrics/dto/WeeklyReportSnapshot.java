package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;
import lombok.Builder;

/**
 * WeeklyReport JSONB 컬럼에 저장될 최종 결과물 스냅샷 (Java 17 Record)
 */
@Builder
public record WeeklyReportSnapshot(
    Summary summary,
    List<UsageItem> usageList,
    List<String> tags,
    int totalScore,
    String scoreLevel,
    Comparison comparison // 지난주 대비 증감 정보
) {}
