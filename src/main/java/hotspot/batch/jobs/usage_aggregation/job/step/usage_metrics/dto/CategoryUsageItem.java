package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Builder;

/**
 * 카테고리별 사용량 및 비중 정보
 */
@Builder
public record CategoryUsageItem(
    String category, // 카테고리명 (STUDY, MEDIA, GAME 등)
    
    // DB에 저장된 옛날 이름(usage)을 현재 이름(thisWeek)으로 바꿔서 읽어오기 위해 필요함
    @JsonAlias({"usage", "thisWeek"}) 
    long usage,     // 사용량 (KB)
    double percent  // 전체 대비 비중 (%)
) {}
