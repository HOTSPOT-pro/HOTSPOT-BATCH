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

        // 1. QueryProvider 설정 (PostgreSQL 전용, 인라인 뷰 방식)
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("family_id, sub_id, name");
        queryProvider.setFromClause("""
                (SELECT fs.family_id, fs.sub_id, m.name 
                 FROM family_report fr
                 JOIN family_sub fs ON fr.family_id = fs.family_id
                 JOIN subscription s ON fs.sub_id = s.sub_id
                 JOIN member m ON s.member_id = m.member_id
                 WHERE fr.receive_day = :receiveDay 
                   AND fr.is_active = true 
                   AND m.is_deleted = false 
                   AND s.is_deleted = false) AS target_data
                """);

        // 바깥쪽 WHERE는 Spring Batch가 페이징을 위해 자동으로 관리하도록 null 처리
        queryProvider.setWhereClause(null);

        // 정렬 키는 이제 유일해진 sub_id 사용
        queryProvider.setSortKeys(Map.of("sub_id", Order.ASCENDING));

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