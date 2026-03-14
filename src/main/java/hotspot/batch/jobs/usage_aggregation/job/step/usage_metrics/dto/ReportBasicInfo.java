package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.time.LocalDate;
import lombok.Builder;

/**
 * Step2 Reader에서 읽어올 주간 리포트의 기본 식별 정보
 * 대상자 선정 및 이후 프로세스의 기준 데이터로 사용됨
 */
@Builder
public record ReportBasicInfo(
    Long weeklyReportId,
    Long familyId,
    Long subId,
    String name,
    LocalDate weekStartDate,
    LocalDate weekEndDate
) {}
