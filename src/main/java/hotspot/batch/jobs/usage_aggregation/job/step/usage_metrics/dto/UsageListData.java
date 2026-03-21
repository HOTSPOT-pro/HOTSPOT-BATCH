package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;
import lombok.Builder;

/**
 * 리포트의 상세 차트용 리스트 데이터 묶음
 * 통합된 카테고리 그룹 정보(thisWeek, lastWeek, comparison)를 포함함
 */
@Builder
public record UsageListData(
    long totalUsage,                      // 이번 주 총 사용량 (KB)
    List<DailyUsageItem> dailyUsageList,   // 일별 사용량 추이 리스트
    List<HourlyUsageItem> hourlyUsageList, // 시간대별 사용량 추이 리스트
    CategoryUsageListContainer categoryUsageList // 카테고리별 통합 통계 객체 (객체 구조로 변경됨)
) {}
