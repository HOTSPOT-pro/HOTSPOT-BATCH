package hotspot.batch.jobs.usage_aggregation.job.step.report_seed.reader;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
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

    private static final Logger log = LoggerFactory.getLogger(ReportSeedReaderConfig.class);

    @Bean
    @StepScope
    public ItemStreamReader<ReportSeedInput> reportSeedReader(
            @Qualifier("mainDataSource") DataSource mainDataSource,
            UsageAggregationDateCalculator dateCalculator,
            @Value("#{jobParameters['targetDate']}") String targetDate) throws Exception {

        String receiveDay = dateCalculator.getReceiveDay(dateCalculator.getBaseDate(targetDate));
        LocalDate weekStartDate = dateCalculator.getWeekStartDate(dateCalculator.getBaseDate(targetDate));

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
                   AND s.is_deleted = false
                   AND fs.family_role = 'CHILD') AS target_data
                """);
        
        queryProvider.setSortKeys(Map.of("sub_id", Order.ASCENDING));
        Map<String, Object> params = Map.of("receiveDay", receiveDay, "weekStartDate", weekStartDate);

        JdbcPagingItemReader<ReportSeedInput> delegate = new JdbcPagingItemReaderBuilder<ReportSeedInput>()
                .name("reportSeedReaderDelegate")
                .dataSource(mainDataSource)
                .queryProvider(queryProvider)
                .parameterValues(params)
                .pageSize(BatchConstants.CHUNK_SIZE)
                .fetchSize(BatchConstants.CHUNK_SIZE)
                .rowMapper(new DataClassRowMapper<>(ReportSeedInput.class))
                .saveState(false)
                .build();

        // 컴파일 에러 해결을 위해 ItemStreamReader 구현체로 반환
        return new ItemStreamReader<ReportSeedInput>() {
            private final AtomicInteger count = new AtomicInteger(0);

            @Override
            public ReportSeedInput read() throws Exception {
                ReportSeedInput item = delegate.read();
                if (item != null) {
                    int current = count.incrementAndGet();
                    if (current % 1000 == 0) {
                        log.info("[Seed-Reader] Reading main DB targets... Cumulative: {}", current);
                    }
                }
                return item;
            }

            @Override
            public void open(ExecutionContext executionContext) throws ItemStreamException { delegate.open(executionContext); }
            @Override
            public void update(ExecutionContext executionContext) throws ItemStreamException { delegate.update(executionContext); }
            @Override
            public void close() throws ItemStreamException { delegate.close(); }
        };
    }
}
