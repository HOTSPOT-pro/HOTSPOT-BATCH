package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;
import lombok.Builder;

/**
 * 지난주 리포트에서 읽어온 상세 사용량 지표 데이터
 * 이번 주 지표와의 세밀한 비교 연산을 위해 DB의 usage_list_data 컬럼을 복원할 때 사용함
 */
@Builder
public record LastWeekUsageListData(
    long totalUsage,
    List<DailyUsageItem> dailyUsageList,
    List<HourlyUsageItem> hourlyUsageList,
    List<CategoryUsageItem> categoryUsageList
) {}
