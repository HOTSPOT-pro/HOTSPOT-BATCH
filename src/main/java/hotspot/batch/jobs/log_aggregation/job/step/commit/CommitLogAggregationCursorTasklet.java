package hotspot.batch.jobs.log_aggregation.job.step.commit;

import hotspot.batch.jobs.log_aggregation.job.LogAggregationProjection;
import hotspot.batch.jobs.log_aggregation.repository.LogAggregationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Component
public class CommitLogAggregationCursorTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(CommitLogAggregationCursorTasklet.class);

    private final LogAggregationRepository logAggregationRepository;

    public CommitLogAggregationCursorTasklet(LogAggregationRepository logAggregationRepository) {
        this.logAggregationRepository = logAggregationRepository;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        var executionContext = chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext();
        long toAppliedSeq = executionContext.getLong(LogAggregationProjection.CTX_TO_APPLIED_SEQ, 0L);

        logAggregationRepository.upsertUsageAggregateCursor(
                LogAggregationProjection.SUB_USAGE_MONTHLY_AGGREGATE,
                toAppliedSeq);

        log.info("Committed aggregation cursor. projectionName={} toAppliedSeq={}",
                LogAggregationProjection.SUB_USAGE_MONTHLY_AGGREGATE, toAppliedSeq);
        return RepeatStatus.FINISHED;
    }
}
