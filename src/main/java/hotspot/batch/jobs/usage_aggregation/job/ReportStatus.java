package hotspot.batch.jobs.usage_aggregation.job;

/**
 * 주간 리포트 생성 상태 Enum
 * PENDING - 집계 대상
 * AGGREAGTED - 집계 완료 후 AI 분석 리포트 생성 대상
 * COMPLETED - AI 분석 리포트 완료
 * FAILED - 실패
 */
public enum ReportStatus {
    PENDING,
    AGGREGATED,
    COMPLETED,
    FAILED
}
