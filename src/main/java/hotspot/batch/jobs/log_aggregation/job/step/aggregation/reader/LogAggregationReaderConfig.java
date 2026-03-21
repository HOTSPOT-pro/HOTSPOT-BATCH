package hotspot.batch.jobs.log_aggregation.job.step.aggregation.reader;

import java.util.Map;
import javax.sql.DataSource;

import hotspot.batch.common.config.BatchConstants;
import hotspot.batch.jobs.log_aggregation.job.LogAggregationProjection;
import hotspot.batch.jobs.log_aggregation.repository.LogAggregationRepository.UsageAppliedEventLogRow;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.support.PostgresPagingQueryProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.RowMapper;

@Configuration
public class LogAggregationReaderConfig {

    @Bean
    @StepScope
    public JdbcPagingItemReader<UsageAppliedEventLogRow> usageAppliedEventLogPagingReader(
            @Qualifier("batchDataSource") DataSource batchDataSource,
            @Value("#{jobExecutionContext['" + LogAggregationProjection.CTX_FROM_APPLIED_SEQ + "']}") Long fromAppliedSeq,
            @Value("#{jobExecutionContext['" + LogAggregationProjection.CTX_TO_APPLIED_SEQ + "']}") Long toAppliedSeq) throws Exception {

        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("""
                applied_seq,
                event_id,
                sub_id,
                family_id,
                app_id,
                yyyymm,
                yyyymmdd,
                usage_amount,
                gift_used,
                plan_used,
                family_used,
                gift_detail_json
                """);
        queryProvider.setFromClause("usage_applied_event_log");
        queryProvider.setWhereClause("applied_seq > :fromAppliedSeq and applied_seq <= :toAppliedSeq");
        queryProvider.setSortKeys(Map.of("applied_seq", Order.ASCENDING));

        Map<String, Object> parameterValues = Map.of(
                "fromAppliedSeq", fromAppliedSeq == null ? 0L : fromAppliedSeq,
                "toAppliedSeq", toAppliedSeq == null ? 0L : toAppliedSeq);

        RowMapper<UsageAppliedEventLogRow> rowMapper = (rs, rowNum) -> new UsageAppliedEventLogRow(
                rs.getLong("applied_seq"),
                rs.getString("event_id"),
                rs.getLong("sub_id"),
                rs.getLong("family_id"),
                rs.getLong("app_id"),
                rs.getString("yyyymm"),
                rs.getString("yyyymmdd"),
                rs.getLong("usage_amount"),
                rs.getLong("gift_used"),
                rs.getLong("plan_used"),
                rs.getLong("family_used"),
                rs.getString("gift_detail_json"));

        return new JdbcPagingItemReaderBuilder<UsageAppliedEventLogRow>()
                .name("usageAppliedEventLogPagingReader")
                .dataSource(batchDataSource)
                .queryProvider(queryProvider)
                .parameterValues(parameterValues)
                .pageSize(BatchConstants.CHUNK_SIZE)
                .rowMapper(rowMapper)
                .build();
    }
}
