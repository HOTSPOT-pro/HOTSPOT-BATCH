package hotspot.batch.jobs.llm_feedback.dto;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ScoreResult;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageListData;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * LLM 피드백 처리를 위한 WeeklyReport DTO
 */
@Builder
public record LlmFeedbackWeeklyReport(
    Long weeklyReportId,
    Long familyId,
    Long subId,
    String name,
    LocalDate weekStartDate,
    LocalDate weekEndDate,
    long totalUsage,
    ScoreResult scoreResult,
    List<String> tags,
    SummaryData summaryData,
    UsageListData usageListData,
    String reportStatus,
    
    // LLM 관련 필드
    AiFeedback aiFeedback,
    boolean isLlmUsed,
    String aiModel,
    String promptVersion
) {
    public LlmFeedbackWeeklyReport withAiFeedback(AiFeedback aiFeedback, String aiModel, String promptVersion) {
        return LlmFeedbackWeeklyReport.builder()
            .weeklyReportId(this.weeklyReportId)
            .familyId(this.familyId)
            .subId(this.subId)
            .name(this.name)
            .weekStartDate(this.weekStartDate)
            .weekEndDate(this.weekEndDate)
            .totalUsage(this.totalUsage)
            .scoreResult(this.scoreResult)
            .tags(this.tags)
            .summaryData(this.summaryData)
            .usageListData(this.usageListData)
            .reportStatus("COMPLETED")
            .aiFeedback(aiFeedback)
            .isLlmUsed(true)
            .aiModel(aiModel)
            .promptVersion(promptVersion)
            .build();
    }
}
