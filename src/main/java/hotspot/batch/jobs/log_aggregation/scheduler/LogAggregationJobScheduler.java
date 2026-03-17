package hotspot.batch.jobs.log_aggregation.scheduler;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LogAggregationJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(LogAggregationJobScheduler.class);
    private static final String LOG_AGGREGATION_JOB_NAME = "logAggregationJob";

    private final JobOperator jobOperator;
    private final Map<String, Job> jobs;
    private final ApplicationArguments applicationArguments;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public LogAggregationJobScheduler(
            JobOperator jobOperator,
            Map<String, Job> jobs,
            ApplicationArguments applicationArguments) {
        this.jobOperator = jobOperator;
        this.jobs = jobs;
        this.applicationArguments = applicationArguments;
    }

    @Scheduled(
            initialDelayString = "${batch.log-aggregation.scheduler.initial-delay-ms:5000}",
            fixedDelayString = "${batch.log-aggregation.scheduler.fixed-delay-ms:2000}")
    public void run() {
        if (hasManualJobExecutionRequest()) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            Job job = jobs.get(LOG_AGGREGATION_JOB_NAME);
            if (job == null) {
                log.warn("Skip scheduled run. Missing job bean name={}", LOG_AGGREGATION_JOB_NAME);
                return;
            }

            JobParametersBuilder builder = new JobParametersBuilder()
                    .addLong("run.id", System.currentTimeMillis());

            JobExecution execution = jobOperator.start(job, builder.toJobParameters());
            if (execution.getStatus() != BatchStatus.COMPLETED) {
                log.error("Scheduled job failed. job={} status={}",
                        LOG_AGGREGATION_JOB_NAME, execution.getStatus());
                return;
            }

            log.info("Scheduled job completed. job={} executionId={}",
                    LOG_AGGREGATION_JOB_NAME, execution.getId());
        } catch (Exception e) {
            log.error("Scheduled job execution failed. job={}", LOG_AGGREGATION_JOB_NAME, e);
        } finally {
            running.set(false);
        }
    }

    private boolean hasManualJobExecutionRequest() {
        return findOption("job.name").isPresent() || findOption("spring.batch.job.name").isPresent();
    }

    private Optional<String> findOption(String key) {
        return Optional.ofNullable(applicationArguments.getOptionValues(key))
                .flatMap(values -> values.stream().findFirst());
    }
}
