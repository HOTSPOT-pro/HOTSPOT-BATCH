package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

/**
 * 수치의 차이(diff)와 변화율(changeRatePct)을 쌍으로 관리하는 공통 객체
 */
public record ComparisonValue(
    long diff,
    double changeRatePct
) {
    public static ComparisonValue zero() {
        return new ComparisonValue(0L, 0.0);
    }
}
