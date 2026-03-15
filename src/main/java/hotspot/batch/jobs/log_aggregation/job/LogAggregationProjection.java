package hotspot.batch.jobs.log_aggregation.job;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogAggregationProjection {

    public static final String SUB_USAGE_MONTHLY_AGGREGATE = "sub_usage_monthly_aggregate";

    public static final String CTX_FROM_APPLIED_SEQ = "logAggregation.fromAppliedSeq";
    public static final String CTX_TO_APPLIED_SEQ = "logAggregation.toAppliedSeq";
}
