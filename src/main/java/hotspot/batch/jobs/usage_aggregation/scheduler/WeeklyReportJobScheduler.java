package hotspot.batch.jobs.usage_aggregation.scheduler;

import hotspot.batch.common.util.ManualJobExecutionChecker;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

import lombok.RequiredArgsConstructor;

/**
 * 주간 리포트 분석 및 AI 피드백 생성 스케줄러 (Job1 -> Job2 순차 실행)
 * 매일 00:30분에 실행되며, Job1(집계)이 성공적으로 완료되었을 때만 Job2(AI 피드백)를 실행함.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "batch.weekly-report.scheduler.enabled", havingValue = "true")
public class WeeklyReportJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportJobScheduler.class);
    private static final String JOB_1_NAME = "usageAggregationJob";
    private static final String JOB_2_NAME = "llmFeedbackJob";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JobOperator jobOperator;
    private final Map<String, Job> jobs;
    private final ManualJobExecutionChecker manualJobExecutionChecker;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 매일 00:30분에 실행 (어제 날짜 데이터를 분석 대상으로 설정)
     */
    @Scheduled(cron = "${batch.weekly-report.scheduler.cron}", zone = "Asia/Seoul")
    public void run() {
        if (manualJobExecutionChecker.hasManualJobExecutionRequest()) {
            log.info("Skip scheduled weekly report. Manual job execution requested.");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.warn("Skip scheduled weekly report. Previous run is still in progress.");
            return;
        }

        try {
            log.info("==== START Scheduled Weekly Report Batch Sequence ====");
            
            // 어제 날짜 기준으로 리포트 생성 (JobParameter 전달용)
            String targetDate = LocalDate.now().minusDays(1).format(DATE_FORMATTER);

            // 1. Job 1 실행: 사용량 집계 및 분석
            JobExecution execution1 = executeJob(JOB_1_NAME, targetDate);
            
            // Job 1이 완료(COMPLETED)된 경우에만 Job 2 실행
            if (execution1 != null && execution1.getStatus() == BatchStatus.COMPLETED) {
                log.info("Job 1 ({}) Success. Proceeding to Job 2 ({}).", JOB_1_NAME, JOB_2_NAME);
                executeJob(JOB_2_NAME, targetDate);
            } else {
                log.error("Job 1 ({}) failed or skipped. Job 2 ({}) will not run.", JOB_1_NAME, JOB_2_NAME);
            }

            log.info("==== END Scheduled Weekly Report Batch Sequence ====");
        } finally {
            running.set(false);
        }
    }

    private JobExecution executeJob(String jobName, String targetDate) {
        try {
            Job job = jobs.get(jobName);
            if (job == null) {
                log.warn("Missing job bean: {}. Skipping execution.", jobName);
                return null;
            }

            log.info("Starting scheduled job: {} with targetDate={}", jobName, targetDate);
            
            JobParametersBuilder builder = new JobParametersBuilder()
                    .addLong("run.id", System.currentTimeMillis())
                    .addString("targetDate", targetDate);

            JobExecution execution = jobOperator.start(job, builder.toJobParameters());
            
            log.info("Job {} execution finished with status: {}", jobName, execution.getStatus());
            return execution;
        } catch (Exception e) {
            log.error("Job execution failed: {}", jobName, e);
            return null;
        }
    }
}
