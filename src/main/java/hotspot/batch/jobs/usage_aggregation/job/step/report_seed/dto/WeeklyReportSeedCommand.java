package hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import hotspot.batch.jobs.usage_aggregation.job.ReportStatus;

/**
 * Step1 processor가 생성한 WeeklyReport seed 데이터를 writer로 전달하는 모델
 */
public record WeeklyReportSeedCommand(
        Long subId,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        LocalDateTime createdDate,
        ReportStatus status) {
}
