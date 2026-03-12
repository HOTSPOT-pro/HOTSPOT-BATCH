package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * 최종적으로 DB에 저장될 WeeklyReport 데이터
 * Writer는 이 객체를 받아 Bulk Update를 수행함
 */
@Builder
public record WeeklyReport(
    Long reportId,
    long totalUsage,
    int totalScore,
    String scoreLevel,
    List<String> tags,
    SummaryData summaryData,
    UsageListData usageListData,
    // TODO: AI 피드백 관련 필드 추가
    String reportStatus
) {}
