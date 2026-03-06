package hotspot.batch.global.runner;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BatchJobRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchJobRunner.class);

    private final JobOperator jobOperator;
    private final Map<String, Job> jobs;

    public BatchJobRunner(JobOperator jobOperator, Map<String, Job> jobs) {
        this.jobOperator = jobOperator;
        this.jobs = jobs;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String jobName = resolveJobName(args).orElse(null);
        if (jobName == null || jobName.isBlank()) {
            log.info("No job name provided. Skip execution.");
            return;
        }

        Job job = jobs.get(jobName);
        if (job == null) {
            throw new IllegalArgumentException("Unknown job.name: " + jobName);
        }

        JobParametersBuilder builder = new JobParametersBuilder().addLong("run.id", System.currentTimeMillis());
        addStringParam(args, builder, "targetDate");
        addStringParam(args, builder, "yearMonth");

        log.info("BATCH START job={}", jobName);
        JobExecution execution = jobOperator.start(job, builder.toJobParameters());

        if (execution.getStatus() != BatchStatus.COMPLETED) {
            log.error("BATCH FAILED job={} status={}", jobName, execution.getStatus());
            throw new IllegalStateException("Batch job failed with status: " + execution.getStatus());
        }

        log.info("BATCH SUCCESS job={} executionId={}", jobName, execution.getId());
    }

    private Optional<String> resolveJobName(ApplicationArguments args) {
        return findOption(args, "job.name")
                .or(() -> findOption(args, "spring.batch.job.name"))
                .or(() -> args.getNonOptionArgs().stream().findFirst());
    }

    private Optional<String> findOption(ApplicationArguments args, String key) {
        return Optional.ofNullable(args.getOptionValues(key)).flatMap(values -> values.stream().findFirst());
    }

    private void addStringParam(ApplicationArguments args, JobParametersBuilder builder, String key) {
        findOption(args, key).ifPresent(value -> builder.addString(key, value));
    }
}
