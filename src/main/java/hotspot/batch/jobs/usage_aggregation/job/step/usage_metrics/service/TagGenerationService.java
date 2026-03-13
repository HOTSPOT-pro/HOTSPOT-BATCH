package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service;

import java.util.List;

import hotspot.batch.jobs.usage_aggregation.job.ReportTag;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageComparisonResult;

/**
 * 이번 주 통계 및 전주 대비 변화를 분석하여 최대 2개의 인사이트 태그를 생성하는 서비스
 */
public interface TagGenerationService {

    /**
     * 분석 데이터를 바탕으로 우선순위에 따라 태그 리스트를 생성함
     * 
     * @param summary 이번 주 요약 통계 (평일/주말 평균, 특정 시간대 사용량 등)
     * @param comparison 전주 대비 증감 통계 (변화량, 비중 변화 등)
     * @param totalUsage 이번 주 총 사용량 (절대량 판정 및 비중 계산용)
     * @return 선택된 최대 2개의 태그 리스트 (없을 시 BALANCED_GOOD 반환)
     */
    List<ReportTag> generate(SummaryData summary, UsageComparisonResult comparison, long totalUsage);
}
