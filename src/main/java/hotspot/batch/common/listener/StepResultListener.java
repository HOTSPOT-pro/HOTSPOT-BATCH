package hotspot.batch.common.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
public class StepResultListener implements StepExecutionListener {

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info(">>> [STEP START] : {} | Parameters: {}", 
                 stepExecution.getStepName(), stepExecution.getJobParameters());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        LocalDateTime startTime = stepExecution.getStartTime();
        LocalDateTime endTime = LocalDateTime.now();
        long durationMillis = Duration.between(startTime, endTime).toMillis();
        double durationSec = durationMillis / 1000.0;
        
        long readCount = stepExecution.getReadCount();
        long writeCount = stepExecution.getWriteCount();
        long commitCount = stepExecution.getCommitCount();
        
        double tps = durationSec > 0 ? (writeCount / durationSec) : 0;

        log.info("--------------------------------------------------");
        log.info(">>> [STEP PERFORMANCE REPORT : {}]", stepExecution.getStepName());
        log.info("> Status         : {}", stepExecution.getExitStatus().getExitCode());
        log.info("> Duration       : {} sec ({} ms)", String.format("%.2f", durationSec), durationMillis);
        log.info("> Throughput     : {} TPS (Items/sec)", String.format("%.2f", tps));
        log.info("> Total Counts   : Read={}, Write={}, Skip={}, Filter={}", 
                 readCount, writeCount, stepExecution.getSkipCount(), stepExecution.getFilterCount());
        log.info("> Commits        : {}, Rollbacks: {}", commitCount, stepExecution.getRollbackCount());
        log.info("--------------------------------------------------");

        return stepExecution.getExitStatus();
    }
}
