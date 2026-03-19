package hotspot.batch.common.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Step 실행 결과를 상세하게 기록하고 성능 지표(TPS, 건당 소요 시간 등)를 산출하는 리스너
 */
@Component
public class StepResultListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(StepResultListener.class);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("--------------------------------------------------");
        log.info(">>> [STEP START] : {}", stepExecution.getStepName());
        log.info("--------------------------------------------------");
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        LocalDateTime start = stepExecution.getStartTime();
        LocalDateTime end = LocalDateTime.now(); // 현재 시점 기준 종료 시간
        long durationMs = (start != null) ? Duration.between(start, end).toMillis() : 0L;
        double durationSec = durationMs / 1000.0;

        long writeCount = stepExecution.getWriteCount();
        long readCount = stepExecution.getReadCount();
        
        // TPS (초당 처리 건수) 계산: 실제 DB에 쓰여진(Write) 건수 기준
        double tps = (durationMs > 0) ? (writeCount / durationSec) : 0;
        
        // 건당 평균 소요 시간 (ms)
        double avgTimePerItem = (writeCount > 0) ? (double) durationMs / writeCount : 0;

        String stepName = stepExecution.getStepName();
        String status = stepExecution.getExitStatus().getExitCode();

        log.info("--------------------------------------------------");
        log.info(">>> [STEP PERFORMANCE REPORT : {}]", stepName);
        log.info("> Status         : {}", status);
        log.info("> Duration       : {} sec ({} ms)", String.format("%.3f", durationSec), durationMs);
        log.info("> Throughput     : {} TPS (Items/sec)", String.format("%.2f", tps));
        log.info("> Avg Processing : {} ms/item", String.format("%.2f", avgTimePerItem));
        log.info("> Total Counts   : Read={}, Write={}, Skip={}, Filter={}", 
                 readCount, writeCount, stepExecution.getSkipCount(), stepExecution.getFilterCount());
        log.info("> Commits        : {}, Rollbacks: {}", stepExecution.getCommitCount(), stepExecution.getRollbackCount());
        log.info("--------------------------------------------------");

        if (stepExecution.getExitStatus().equals(ExitStatus.FAILED)) {
            stepExecution.getFailureExceptions().forEach(e -> 
                log.error("!!! [STEP ERROR] {} failed with exception: ", stepName, e));
        }
        
        return stepExecution.getExitStatus();
    }
}
