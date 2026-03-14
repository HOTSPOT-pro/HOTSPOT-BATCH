package hotspot.batch.jobs.usage_aggregation.job.step.report_seed;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import hotspot.batch.common.config.BatchConstants;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.ReportSeedInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReport;

/**
 * Step1: 리포트 생성 대상자 선정 및 Seed 데이터 생성 (Chunk 방식)
 * 메인 DB(hotspot)에서 데이터를 읽어 배치 DB(hotspot-batch)로 전송함
 */
@Configuration
public class ReportSeedStepConfig {

    @Bean
    public Step reportSeedStep(
            JobRepository jobRepository,
            @Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager, // 배치 DB의 트랜잭션 매니저 주입
            JdbcPagingItemReader<ReportSeedInput> reportSeedReader,
            ItemProcessor<ReportSeedInput, WeeklyReport> reportSeedProcessor,
            JdbcBatchItemWriter<WeeklyReport> reportSeedWriter) {

        return new StepBuilder("reportSeedStep", jobRepository)
                .<ReportSeedInput, WeeklyReport>chunk(BatchConstants.CHUNK_SIZE, batchTransactionManager)
                .reader(reportSeedReader)
                .processor(reportSeedProcessor)
                .writer(reportSeedWriter)
                .build();
    }
}