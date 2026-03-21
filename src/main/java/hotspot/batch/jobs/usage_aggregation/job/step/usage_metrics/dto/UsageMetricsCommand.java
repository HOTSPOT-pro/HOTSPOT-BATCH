package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.Map;

/**
 * Step2 processor가 계산한 지표/태그/점수/스냅샷 정보를 writer로 전달하는 모델
 */
public record UsageMetricsCommand(
        Long weeklyReportId,
        Integer score,
        String tag,
        Map<String, Object> metrics,
        Map<String, Object> snapshot) {
}
