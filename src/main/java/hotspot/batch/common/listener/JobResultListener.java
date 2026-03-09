package hotspot.batch.common.listener;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

@Component
public class JobResultListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobResultListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("JOB START job={} executionId={}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getId());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        LocalDateTime start = jobExecution.getStartTime();
        LocalDateTime end = jobExecution.getEndTime();
        long durationMs = (start != null && end != null) ? Duration.between(start, end).toMillis() : 0L;

        if (jobExecution.getStatus().isUnsuccessful()) {
            log.error("JOB FAILED job={} status={} durationMs={}",
                    jobExecution.getJobInstance().getJobName(),
                    jobExecution.getStatus(),
                    durationMs);
            return;
        }

        log.info("JOB SUCCESS job={} status={} durationMs={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getStatus(),
                durationMs);
    }
}
