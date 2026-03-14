package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;
import lombok.Builder;

/**
 * 리포트의 상세 사용량 지표 리스트 데이터 (차트용 통합 객체)
 * PostgreSQL JSONB 컬럼에 직렬화되어 저장됨
 */
@Builder
public record UsageListData(
    long totalUsage,
    List<DailyUsageItem> dailyUsageList,
    List<HourlyUsageItem> hourlyUsageList,
    List<CategoryUsageItem> categoryUsageList
) {}
