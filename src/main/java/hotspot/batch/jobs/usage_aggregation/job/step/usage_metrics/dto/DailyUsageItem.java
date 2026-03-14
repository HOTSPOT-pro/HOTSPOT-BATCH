package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Builder;

/**
 * 일별 합산 및 비교 사용량 정보 (차트 데이터용)
 */
@Builder
public record DailyUsageItem(
    String date,      // yyyy-MM-dd
    String day,       // MONDAY, TUESDAY ...
    
    // DB에 이미 저장된 옛날 이름(totalUsage)을 현재 이름(thisWeek)으로 바꿔서 읽어오기 위해 필요함
    @JsonAlias("totalUsage") long thisWeek,    // 이번 주 사용량 (KB)
    long lastWeek     // 지난주 사용량 (KB)
) {}
