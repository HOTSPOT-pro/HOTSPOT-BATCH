package hotspot.batch.jobs.usage_aggregation.job.step.report_seed;


import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Step1: WeeklyReport seed 생성 구성을 정의하는 설정
 * 복잡한 Partitioning 대신 단일 SQL Tasklet 방식으로 단순화
 */
@Configuration
public class ReportSeedStepConfig {

    private final ReportSeedTasklet reportSeedTasklet;

    public ReportSeedStepConfig(ReportSeedTasklet reportSeedTasklet) {
        this.reportSeedTasklet = reportSeedTasklet;
    }

    @Bean
    public Step reportSeedStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager) {
        return new StepBuilder("reportSeedStep", jobRepository)
                .tasklet(reportSeedTasklet, transactionManager)
                .build();
    }
}
