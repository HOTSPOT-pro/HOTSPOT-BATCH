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
    Long weeklyReportId, // PK
    Long familyId,       // 가족 식별자
    Long subId,          // 유저 식별자
    String name,         // 유저 이름
    LocalDate weekStartDate, // 분석 시작일
    LocalDate weekEndDate,   // 분석 종료일
    long totalUsage,
    ScoreResult scoreResult, // 점수, 등급, 사유 통합
    List<String> tags,
    SummaryData summaryData,
    UsageListData usageListData, // 이번 주 상세 리스트
    String reportStatus
) {}
