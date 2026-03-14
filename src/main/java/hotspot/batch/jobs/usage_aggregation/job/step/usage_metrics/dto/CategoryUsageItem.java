package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Builder;

/**
 * 카테고리별 합산 및 비교 사용량 정보 (차트 데이터용)
 */
@Builder
public record CategoryUsageItem(
    String category, // 카테고리명 (STUDY, MEDIA, GAME 등)
    
    // DB에 저장된 옛날 이름(usage)을 현재 이름(thisWeek)으로 바꿔서 읽어오기 위해 필요함
    @JsonAlias("usage") long thisWeek,   // 이번 주 사용량 (KB)
    long lastWeek,   // 지난주 사용량 (KB)
    double changeRate // 전주 대비 증감률 (%)
) {}
