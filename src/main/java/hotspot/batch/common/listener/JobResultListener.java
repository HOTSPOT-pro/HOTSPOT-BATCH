package hotspot.batch.common.listener;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * Job 실행 전체의 시작/종료와 총 소요 시간을 요약하여 기록하는 리스너
 */
@Component
public class JobResultListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobResultListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("==================================================");
        log.info(">>> [JOB START] : {}", jobExecution.getJobInstance().getJobName());
        log.info(">>> Execution ID : {}", jobExecution.getId());
        log.info("==================================================");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        LocalDateTime start = jobExecution.getStartTime();
        LocalDateTime end = LocalDateTime.now();
        long durationMs = (start != null) ? Duration.between(start, end).toMillis() : 0L;
        double durationMin = durationMs / 60000.0;

        log.info("==================================================");
        log.info(">>> [JOB SUMMARY : {}]", jobExecution.getJobInstance().getJobName());
        log.info("> Final Status   : {}", jobExecution.getStatus());
        log.info("> Total Duration : {} min ({} sec)", 
                 String.format("%.2f", durationMin), String.format("%.3f", durationMs / 1000.0));
        
        if (jobExecution.getStatus().isUnsuccessful()) {
            log.error(">>> [JOB FAILED] Check the step error logs for root cause.");
        }
        log.info("==================================================");
    }
}
