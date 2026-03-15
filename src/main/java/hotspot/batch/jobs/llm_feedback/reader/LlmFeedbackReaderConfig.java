package hotspot.batch.jobs.llm_feedback.reader;

import hotspot.batch.jobs.llm_feedback.dto.LlmFeedbackWeeklyReport;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.PagingQueryProvider;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.DataClassRowMapper;

/**
 * LLM 피드백 처리를 위한 WeeklyReport 데이터를 읽어오는 Reader 설정
 */
@Configuration
@RequiredArgsConstructor
public class LlmFeedbackReaderConfig {

    private final DataSource dataSource;
    private static final int CHUNK_SIZE = 50;

    @Bean
    @StepScope
    public JdbcPagingItemReader<LlmFeedbackWeeklyReport> llmFeedbackReader(
            @Value("#{jobParameters[targetDate]}") String targetDate) throws Exception {

        Map<String, Object> parameterValues = new HashMap<>();
        parameterValues.put("targetDate", targetDate);
        parameterValues.put("status", "AGGREGATED");

        return new JdbcPagingItemReaderBuilder<LlmFeedbackWeeklyReport>()
                .name("llmFeedbackReader")
                .dataSource(dataSource)
                .queryProvider(createPagingQueryProvider())
                .parameterValues(parameterValues)
                .pageSize(CHUNK_SIZE)
                .rowMapper(new DataClassRowMapper<>(LlmFeedbackWeeklyReport.class))
                .build();
    }

    @Bean
    public PagingQueryProvider createPagingQueryProvider() {
        SqlPagingQueryProviderFactoryBean queryProvider = new SqlPagingQueryProviderFactoryBean();
        queryProvider.setDataSource(dataSource);
        queryProvider.setSelectClause("SELECT weekly_report_id, family_id, sub_id, name, week_start_date, week_end_date, total_usage, score_result, tags, summary_data, usage_list_data, report_status");
        queryProvider.setFromClause("FROM weekly_report");
        queryProvider.setWhereClause("WHERE report_status = :status AND week_start_date = :targetDate::date");
        queryProvider.setSortKeys(Map.of("weekly_report_id", Order.ASCENDING));

        try {
            return queryProvider.getObject();
        } catch (Exception e) {
            throw new RuntimeException("PagingQueryProvider 생성 실패", e);
        }
    }
}
