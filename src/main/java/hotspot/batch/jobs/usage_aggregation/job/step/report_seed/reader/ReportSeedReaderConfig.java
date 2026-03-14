package hotspot.batch.jobs.usage_aggregation.job.step.report_seed.reader;

import java.util.Map;
import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.support.PostgresPagingQueryProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.DataClassRowMapper;

import hotspot.batch.common.config.BatchConstants;
import hotspot.batch.jobs.usage_aggregation.job.UsageAggregationDateCalculator;
import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.ReportSeedInput;

@Configuration
public class ReportSeedReaderConfig {

    @Bean
    @StepScope
    public JdbcPagingItemReader<ReportSeedInput> reportSeedReader(
            @Qualifier("mainDataSource") DataSource mainDataSource,
            UsageAggregationDateCalculator dateCalculator,
            @Value("#{jobParameters['targetDate']}") String targetDate) throws Exception {

        String receiveDay = dateCalculator.getReceiveDay(dateCalculator.getBaseDate(targetDate));

        // 1. QueryProvider 설정 (PostgreSQL 전용)
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("fs.family_id, fs.sub_id, m.name");
        queryProvider.setFromClause("""
                from family_report fr
                join family_sub fs on fr.family_id = fs.family_id
                join subscription s on fs.sub_id = s.sub_id
                join member m on s.member_id = m.member_id
                """);
        queryProvider.setWhereClause("""
                fr.receive_day = :receiveDay 
                and fr.is_active = true 
                and m.is_deleted = false 
                and s.is_deleted = false
                """);

        // 주의: org.springframework.batch.item.database.Order 임포트 필요
        queryProvider.setSortKeys(Map.of("fs.sub_id", Order.ASCENDING));

        // 2. Builder를 이용한 Reader 생성 및 반환
        return new JdbcPagingItemReaderBuilder<ReportSeedInput>()
                .name("reportSeedReader")
                .dataSource(mainDataSource)
                .queryProvider(queryProvider)
                .parameterValues(Map.of("receiveDay", receiveDay))
                .pageSize(BatchConstants.CHUNK_SIZE)
                .rowMapper(new DataClassRowMapper<>(ReportSeedInput.class))
                .build();
    }
}