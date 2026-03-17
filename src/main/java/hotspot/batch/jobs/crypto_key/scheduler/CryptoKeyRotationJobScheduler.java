package hotspot.batch.jobs.crypto_key.scheduler;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import hotspot.batch.common.util.ManualJobExecutionChecker;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "batch.crypto-key.scheduler.enabled", havingValue = "true")
public class CryptoKeyRotationJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(CryptoKeyRotationJobScheduler.class);
    private static final String JOB_NAME = "cryptoKeyRotationJob";

    private final JobOperator jobOperator;
    private final Map<String, Job> jobs;
    private final ManualJobExecutionChecker manualJobExecutionChecker;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "${batch.crypto-key.scheduler.cron}", zone = "Asia/Seoul")
    public void run() {
        if (manualJobExecutionChecker.hasManualJobExecutionRequest()) {
            log.info("Skip scheduled crypto key rotation. Manual job execution requested.");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.warn("Skip scheduled crypto key rotation. Previous run is still in progress.");
            return;
        }

        try {
            Job job = jobs.get(JOB_NAME);
            if (job == null) {
                log.warn("Skip scheduled crypto key rotation. Missing job bean name={}", JOB_NAME);
                return;
            }

            log.info("==== START Scheduled Crypto Key Rotation Batch ====");

            JobExecution execution = jobOperator.start(
                    job,
                    new JobParametersBuilder()
                            .addLong("run.id", System.currentTimeMillis())
                            .toJobParameters());

            if (execution.getStatus() != BatchStatus.COMPLETED) {
                log.error("Scheduled crypto key rotation failed. job={} status={}", JOB_NAME, execution.getStatus());
                return;
            }

            log.info("Scheduled crypto key rotation completed. job={} executionId={}", JOB_NAME, execution.getId());
            log.info("==== END Scheduled Crypto Key Rotation Batch ====");
        } catch (Exception e) {
            log.error("Scheduled crypto key rotation execution failed. job={}", JOB_NAME, e);
        } finally {
            running.set(false);
        }
    }
}
