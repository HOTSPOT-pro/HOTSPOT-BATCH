package hotspot.batch.jobs.usage_aggregation.job.step.report_seed;

import java.util.Map;

import javax.sql.DataSource;


import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;

import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.support.PostgresPagingQueryProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import hotspot.batch.common.config.BatchConstants;
import hotspot.batch.jobs.usage_aggregation.job.UsageAggregationDateCalculator;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.ReportSeedInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReport;

/**
 * Step1: 리포트 생성 대상자 선정 및 Seed 데이터 생성 (Chunk 방식)
 * 메인 DB(hotspot)에서 데이터를 읽어 배치 DB(hotspot-batch)로 전송함
 */
@Configuration
public class ReportSeedStepConfig {

    private final UsageAggregationDateCalculator dateCalculator;

    public ReportSeedStepConfig(UsageAggregationDateCalculator dateCalculator) {
        this.dateCalculator = dateCalculator;
    }

    @Bean
    public Step reportSeedStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcPagingItemReader<ReportSeedInput> reportSeedReader,
            ReportSeedProcessor reportSeedProcessor,
            JdbcBatchItemWriter<WeeklyReport> reportSeedWriter) {
        
        return new StepBuilder("reportSeedStep", jobRepository)
                .<ReportSeedInput, WeeklyReport>chunk(BatchConstants.CHUNK_SIZE, transactionManager)
                .reader(reportSeedReader)
                .processor(reportSeedProcessor)
                .writer(reportSeedWriter)
                .build();
    }

    /**
     * 메인 DB에서 오늘 리포트 발송 대상 가족 구성원을 조회함
     */
    @Bean
    @StepScope
    public JdbcPagingItemReader<ReportSeedInput> reportSeedReader(
            @Qualifier("mainDataSource") DataSource mainDataSource,
            @Value("#{jobParameters['targetDate']}") String targetDate) throws Exception {
        
        String receiveDay = dateCalculator.getReceiveDay(dateCalculator.getBaseDate(targetDate));

        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("fs.family_id, fs.sub_id, m.name");
        queryProvider.setFromClause("""
                from family_report fr
                join family_sub fs on fr.family_id = fs.family_id
                join subscription s on fs.sub_id = s.sub_id
                join member m on s.member_id = m.member_id
                """);
        queryProvider.setWhereClause("fr.receive_day = :receiveDay and fr.is_active = true and m.is_deleted = false and s.is_deleted = false");
        queryProvider.setSortKeys(Map.of("fs.sub_id", Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<ReportSeedInput>()
                .name("reportSeedReader")
                .dataSource(mainDataSource)
                .queryProvider(queryProvider)
                .parameterValues(Map.of("receiveDay", receiveDay))
                .pageSize(BatchConstants.CHUNK_SIZE)
                .rowMapper(new DataClassRowMapper<>(ReportSeedInput.class))
                .build();
    }

    /**
     * 배치 DB의 weekly_report 테이블에 시드 데이터를 삽입함
     */
    @Bean
    public JdbcBatchItemWriter<WeeklyReport> reportSeedWriter(
            @Qualifier("batchDataSource") DataSource batchDataSource,
            UsageAggregationDateCalculator dateCalculator) {
        
        String sql = """
                INSERT INTO weekly_report (
                    family_id, sub_id, name, week_start_date, week_end_date, report_status
                ) VALUES (
                    :familyId, :subId, :name, :weekStartDate, :weekEndDate, :reportStatus::report_status_enum
                ) ON CONFLICT (sub_id, week_start_date) DO NOTHING
                """;

        return new JdbcBatchItemWriterBuilder<WeeklyReport>()
                .dataSource(batchDataSource)
                .sql(sql)
                .beanMapped()
                .build();
    }
}
