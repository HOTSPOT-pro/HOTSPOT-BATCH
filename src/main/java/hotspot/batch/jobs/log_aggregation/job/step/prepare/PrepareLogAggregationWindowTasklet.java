package hotspot.batch.jobs.log_aggregation.job.step.prepare;

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
public class PrepareLogAggregationWindowTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(PrepareLogAggregationWindowTasklet.class);

    private final LogAggregationRepository logAggregationRepository;

    public PrepareLogAggregationWindowTasklet(LogAggregationRepository logAggregationRepository) {
        this.logAggregationRepository = logAggregationRepository;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long fromAppliedSeq =
                logAggregationRepository.findLastAppliedSeq(LogAggregationProjection.SUB_USAGE_MONTHLY_AGGREGATE);
        long toAppliedSeq = logAggregationRepository.findMaxAppliedSeq();

        var executionContext = chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext();
        executionContext.putLong(LogAggregationProjection.CTX_FROM_APPLIED_SEQ, fromAppliedSeq);
        executionContext.putLong(LogAggregationProjection.CTX_TO_APPLIED_SEQ, toAppliedSeq);

        log.info("Prepared aggregation window. projectionName={} fromAppliedSeq={} toAppliedSeq={}",
                LogAggregationProjection.SUB_USAGE_MONTHLY_AGGREGATE, fromAppliedSeq, toAppliedSeq);
        return RepeatStatus.FINISHED;
    }
}
