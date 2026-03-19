package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * 최종적으로 DB에 저장될 WeeklyReport 데이터
 * [최종 최적화] Writer의 연산 부하를 줄이기 위해 미리 직렬화된 JSON 문자열 필드 추가
 */
@Builder
public record WeeklyReport(
        Long weeklyReportId,
        Long familyId,
        Long subId,
        String name,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        long totalUsage,
        ScoreData scoreData,
        List<String> tags,
        SummaryData summaryData,
        UsageListData usageListData,
        String reportStatus,
        
        // [추가] Writer의 성능 향상을 위해 미리 변환된 JSON 필드
        String scoreJson,
        String summaryJson,
        String usageListJson
) {}
