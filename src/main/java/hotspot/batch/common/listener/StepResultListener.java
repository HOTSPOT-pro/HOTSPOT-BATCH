package hotspot.batch.common.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class StepResultListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(StepResultListener.class);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("STEP START step={} job={} executionId={}",
                stepExecution.getStepName(),
                stepExecution.getJobExecution().getJobInstance().getJobName(),
                stepExecution.getJobExecutionId());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        LocalDateTime start = stepExecution.getStartTime();
        LocalDateTime end = stepExecution.getEndTime();
        long durationMs = (start != null && end != null) ? Duration.between(start, end).toMillis() : 0L;

        String stepName = stepExecution.getStepName();
        String jobName = stepExecution.getJobExecution().getJobInstance().getJobName();
        long executionId = stepExecution.getJobExecutionId();
        String status = stepExecution.getExitStatus().getExitCode();
        long readCount = stepExecution.getReadCount();
        long writeCount = stepExecution.getWriteCount();
        long commitCount = stepExecution.getCommitCount();
        long rollbackCount = stepExecution.getRollbackCount();
        long filterCount = stepExecution.getFilterCount();

        if (stepExecution.getExitStatus().equals(ExitStatus.FAILED)) {
            log.error("STEP FAILED step={} job={} executionId={} status={} read={} write={} commit={} rollback={} filter={} durationMs={}",
                    stepName, jobName, executionId, status, readCount, writeCount, commitCount, rollbackCount, filterCount, durationMs);
        } else {
            log.info("STEP FAILED step={} job={} executionId={} status={} read={} write={} commit={} rollback={} filter={} durationMs={}",
                    stepName, jobName, executionId, status, readCount, writeCount, commitCount, rollbackCount, filterCount, durationMs);
        }

        if (stepExecution.getFailureExceptions() != null && !stepExecution.getFailureExceptions().isEmpty()) {
            stepExecution.getFailureExceptions().forEach(e -> log.error("Step {} failed with exception: {}", stepName, e));
        }
        
        return stepExecution.getExitStatus();
    }
}
