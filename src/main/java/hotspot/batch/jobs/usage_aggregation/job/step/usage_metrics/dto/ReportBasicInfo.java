package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.time.LocalDate;

/**
 * Step2 Reader에서 읽어올 기본 주간 리포트 정보
 */
public record ReportBasicInfo(
    Long reportId,
    Long subId,
    LocalDate weekStartDate,
    LocalDate weekEndDate
) {}
